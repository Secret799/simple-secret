#!/usr/bin/env python3
"""Calculate the Maven reactor modules affected by a Git diff."""

from __future__ import annotations

import os
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import deque
from pathlib import Path

MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0"
NAMESPACE = {"m": MAVEN_NAMESPACE}
INTERNAL_GROUP_ID = "com.ss"


def element_text(parent: ET.Element, path: str) -> str | None:
    element = parent.find(path, NAMESPACE)
    if element is None or element.text is None:
        return None
    value = element.text.strip()
    return value or None


def parse_pom(pom_path: Path) -> dict[str, object]:
    root = ET.parse(pom_path).getroot()
    parent = root.find("m:parent", NAMESPACE)

    group_id = element_text(root, "m:groupId")
    parent_artifact_id = None
    if parent is not None:
        group_id = group_id or element_text(parent, "m:groupId")
        parent_artifact_id = element_text(parent, "m:artifactId")

    artifact_id = element_text(root, "m:artifactId")
    if artifact_id is None:
        raise ValueError(f"Missing artifactId in {pom_path}")

    internal_dependencies: set[str] = set()
    dependency_paths = (
        "m:dependencies/m:dependency",
        "m:profiles/m:profile/m:dependencies/m:dependency",
    )
    for dependency_path in dependency_paths:
        for dependency in root.findall(dependency_path, NAMESPACE):
            dependency_group_id = element_text(dependency, "m:groupId")
            dependency_artifact_id = element_text(dependency, "m:artifactId")
            if (
                dependency_group_id == INTERNAL_GROUP_ID
                and dependency_artifact_id
                and dependency_artifact_id != artifact_id
            ):
                internal_dependencies.add(dependency_artifact_id)

    submodules = [
        module.text.strip()
        for module in root.findall("m:modules/m:module", NAMESPACE)
        if module.text and module.text.strip()
    ]

    skip_deploy = False
    plugin_paths = (
        "m:build/m:plugins/m:plugin",
        "m:build/m:pluginManagement/m:plugins/m:plugin",
    )
    for plugin_path in plugin_paths:
        for plugin in root.findall(plugin_path, NAMESPACE):
            if element_text(plugin, "m:artifactId") != "maven-deploy-plugin":
                continue
            skip_value = element_text(plugin, "m:configuration/m:skip")
            if skip_value and skip_value.lower() == "true":
                skip_deploy = True

    return {
        "artifact_id": artifact_id,
        "group_id": group_id,
        "parent_artifact_id": parent_artifact_id,
        "internal_dependencies": internal_dependencies,
        "submodules": submodules,
        "skip_deploy": skip_deploy,
    }


def scan_reactor(
    root_dir: Path,
) -> tuple[dict[str, dict[str, object]], dict[str, str]]:
    """Scan only modules reachable from the root pom.xml reactor."""

    root_dir = root_dir.resolve()
    root_info = parse_pom(root_dir / "pom.xml")
    modules_by_dir: dict[str, dict[str, object]] = {}
    modules_by_artifact: dict[str, str] = {}
    visited: set[Path] = set()

    def visit(module_dir: Path) -> None:
        module_dir = module_dir.resolve()
        try:
            relative_dir = module_dir.relative_to(root_dir).as_posix()
        except ValueError as error:
            raise ValueError(
                f"Reactor module is outside the repository: {module_dir}"
            ) from error

        if module_dir in visited:
            return
        visited.add(module_dir)

        pom_path = module_dir / "pom.xml"
        if not pom_path.is_file():
            raise FileNotFoundError(f"Missing reactor module pom.xml: {pom_path}")

        info = parse_pom(pom_path)
        artifact_id = str(info["artifact_id"])
        if artifact_id in modules_by_artifact:
            previous_dir = modules_by_artifact[artifact_id]
            raise ValueError(
                f"Duplicate reactor artifactId {artifact_id}: "
                f"{previous_dir} and {relative_dir}"
            )

        modules_by_dir[relative_dir] = info
        modules_by_artifact[artifact_id] = relative_dir

        for submodule in info["submodules"]:
            visit(module_dir / str(submodule))

    for submodule in root_info["submodules"]:
        visit(root_dir / str(submodule))

    return modules_by_dir, modules_by_artifact


def git_ref_exists(root_dir: Path, ref: str) -> bool:
    result = subprocess.run(
        ["git", "rev-parse", "--verify", f"{ref}^{{commit}}"],
        cwd=root_dir,
        capture_output=True,
        text=True,
        check=False,
    )
    return result.returncode == 0


def get_changed_files(
    root_dir: Path, from_ref: str, to_ref: str
) -> list[str] | None:
    if not git_ref_exists(root_dir, from_ref) or not git_ref_exists(root_dir, to_ref):
        return None

    result = subprocess.run(
        [
            "git",
            "diff",
            "--no-renames",
            "--name-only",
            f"{from_ref}..{to_ref}",
        ],
        cwd=root_dir,
        capture_output=True,
        text=True,
        check=True,
    )
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def find_module_for_file(
    file_path: str, modules_by_dir: dict[str, dict[str, object]]
) -> str | None:
    normalized_path = file_path.replace(os.sep, "/").lstrip("./")
    for module_dir in sorted(modules_by_dir, key=len, reverse=True):
        if normalized_path == module_dir or normalized_path.startswith(
            f"{module_dir}/"
        ):
            return module_dir
    return None


def propagate_affected_modules(
    directly_affected: set[str],
    modules_by_dir: dict[str, dict[str, object]],
    modules_by_artifact: dict[str, str],
) -> set[str]:
    downstream: dict[str, set[str]] = {}

    for module_dir, info in modules_by_dir.items():
        artifact_id = str(info["artifact_id"])

        for dependency_artifact_id in info["internal_dependencies"]:
            if dependency_artifact_id in modules_by_artifact:
                downstream.setdefault(str(dependency_artifact_id), set()).add(
                    artifact_id
                )

        parent_artifact_id = info["parent_artifact_id"]
        if parent_artifact_id in modules_by_artifact:
            downstream.setdefault(str(parent_artifact_id), set()).add(artifact_id)

        for submodule in info["submodules"]:
            child_dir = (Path(module_dir) / str(submodule)).as_posix()
            child_info = modules_by_dir.get(child_dir)
            if child_info is not None:
                downstream.setdefault(artifact_id, set()).add(
                    str(child_info["artifact_id"])
                )

    affected = set(directly_affected)
    queue = deque(directly_affected)
    while queue:
        artifact_id = queue.popleft()
        for dependent in downstream.get(artifact_id, set()):
            if dependent not in affected:
                affected.add(dependent)
                queue.append(dependent)

    return affected


def emit_result(
    *,
    changed: bool,
    full_build: bool,
    module_list: str = "",
    directly_changed: set[str] | None = None,
    all_affected: set[str] | None = None,
) -> None:
    print(f"changed={str(changed).lower()}")
    print(f"full_build={str(full_build).lower()}")
    print(f"module_list={module_list}")
    print(f"debug_direct_changed={' '.join(sorted(directly_changed or set()))}")
    print(f"debug_all_affected={' '.join(sorted(all_affected or set()))}")


def main() -> int:
    root_dir = Path(
        subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
    )
    from_ref = sys.argv[1] if len(sys.argv) > 1 else "HEAD~1"
    to_ref = sys.argv[2] if len(sys.argv) > 2 else "HEAD"

    modules_by_dir, modules_by_artifact = scan_reactor(root_dir)
    changed_files = get_changed_files(root_dir, from_ref, to_ref)

    if changed_files is None:
        emit_result(changed=True, full_build=True)
        return 0

    if not changed_files:
        emit_result(changed=False, full_build=False)
        return 0

    full_build_files = {"pom.xml", ".flattened-pom.xml", "mvnw", "mvnw.cmd"}
    if any(
        file_path in full_build_files or file_path.startswith(".mvn/")
        for file_path in changed_files
    ):
        emit_result(changed=True, full_build=True)
        return 0

    directly_changed_dirs = {
        module_dir
        for file_path in changed_files
        if (module_dir := find_module_for_file(file_path, modules_by_dir))
    }
    if not directly_changed_dirs:
        emit_result(changed=False, full_build=False)
        return 0

    directly_changed_artifacts = {
        str(modules_by_dir[module_dir]["artifact_id"])
        for module_dir in directly_changed_dirs
    }
    all_affected_artifacts = propagate_affected_modules(
        directly_changed_artifacts,
        modules_by_dir,
        modules_by_artifact,
    )

    deploy_dirs = sorted(
        {
            modules_by_artifact[artifact_id]
            for artifact_id in all_affected_artifacts
            if artifact_id in modules_by_artifact
            and not bool(
                modules_by_dir[modules_by_artifact[artifact_id]]["skip_deploy"]
            )
        }
    )
    if not deploy_dirs:
        emit_result(
            changed=False,
            full_build=False,
            directly_changed=directly_changed_artifacts,
            all_affected=all_affected_artifacts,
        )
        return 0

    emit_result(
        changed=True,
        full_build=False,
        module_list=",".join(deploy_dirs),
        directly_changed=directly_changed_artifacts,
        all_affected=all_affected_artifacts,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
