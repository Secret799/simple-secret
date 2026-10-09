#!/usr/bin/env python3
"""Verify published modules, BOM entries, and starter consumer coverage."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET


PROJECT_ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


def parse_pom(path: Path) -> ET.Element:
    if not path.is_file():
        raise ValueError(f"Missing POM: {path.relative_to(PROJECT_ROOT)}")
    return ET.parse(path).getroot()


def required_text(root: ET.Element, expression: str, path: Path) -> str:
    element = root.find(expression, NAMESPACE)
    if element is None or element.text is None or not element.text.strip():
        raise ValueError(
            f"Missing {expression} in {path.relative_to(PROJECT_ROOT)}"
        )
    return element.text.strip()


def module_paths(pom_path: Path) -> list[Path]:
    root = parse_pom(pom_path)
    modules = []
    for element in root.findall("m:modules/m:module", NAMESPACE):
        if element.text and element.text.strip():
            modules.append((pom_path.parent / element.text.strip()).resolve())
    return modules


def artifact_id(pom_path: Path) -> str:
    return required_text(parse_pom(pom_path), "m:artifactId", pom_path)


def packaging(pom_path: Path) -> str:
    root = parse_pom(pom_path)
    element = root.find("m:packaging", NAMESPACE)
    return "jar" if element is None or not element.text else element.text.strip()


def managed_simple_secret_artifacts(pom_path: Path) -> set[str]:
    root = parse_pom(pom_path)
    artifacts = set()
    for dependency in root.findall(
        "m:dependencyManagement/m:dependencies/m:dependency", NAMESPACE
    ):
        group = dependency.find("m:groupId", NAMESPACE)
        artifact = dependency.find("m:artifactId", NAMESPACE)
        if (
            group is not None
            and group.text == "com.ss"
            and artifact is not None
            and artifact.text
        ):
            artifacts.add(artifact.text.strip())
    return artifacts


def direct_simple_secret_dependencies(pom_path: Path) -> set[str]:
    root = parse_pom(pom_path)
    artifacts = set()
    for dependency in root.findall("m:dependencies/m:dependency", NAMESPACE):
        group = dependency.find("m:groupId", NAMESPACE)
        artifact = dependency.find("m:artifactId", NAMESPACE)
        if (
            group is not None
            and group.text == "com.ss"
            and artifact is not None
            and artifact.text
        ):
            artifacts.add(artifact.text.strip())
    return artifacts


def describe_difference(name: str, expected: set[str], actual: set[str]) -> list[str]:
    errors = []
    missing = sorted(expected - actual)
    unexpected = sorted(actual - expected)
    if missing:
        errors.append(f"{name} is missing: {', '.join(missing)}")
    if unexpected:
        errors.append(f"{name} has unexpected entries: {', '.join(unexpected)}")
    return errors


def main() -> int:
    errors = []
    root_pom = PROJECT_ROOT / "pom.xml"
    bom_pom = (
        PROJECT_ROOT
        / "simple-secret-common"
        / "simple-secret-common-bom"
        / "pom.xml"
    )
    aggregator_poms = [
        PROJECT_ROOT / "simple-secret-common" / "pom.xml",
        PROJECT_ROOT / "simple-secret-plugins" / "pom.xml",
        PROJECT_ROOT / "simple-secret-springboot-starter" / "pom.xml",
    ]

    publishable_artifacts = set()
    for aggregator_pom in aggregator_poms:
        for module_path in module_paths(aggregator_pom):
            module_pom = module_path / "pom.xml"
            if packaging(module_pom) != "pom":
                publishable_artifacts.add(artifact_id(module_pom))

    root_managed = managed_simple_secret_artifacts(root_pom)
    bom_managed = managed_simple_secret_artifacts(bom_pom)
    errors.extend(
        describe_difference("Root dependencyManagement", publishable_artifacts, root_managed)
    )
    errors.extend(describe_difference("Public BOM", publishable_artifacts, bom_managed))

    starter_pom = PROJECT_ROOT / "simple-secret-springboot-starter" / "pom.xml"
    starter_artifacts = {
        artifact_id(module_path / "pom.xml") for module_path in module_paths(starter_pom)
    }

    consumers_pom = PROJECT_ROOT / "integration-tests" / "pom.xml"
    listed_consumer_paths = module_paths(consumers_pom)
    listed_consumer_names = {path.name for path in listed_consumer_paths}
    actual_consumer_names = {
        path.name
        for path in consumers_pom.parent.glob("consumer-*")
        if path.is_dir()
    }
    errors.extend(
        describe_difference(
            "Integration-test module list", actual_consumer_names, listed_consumer_names
        )
    )

    artifact_consumers: dict[str, list[str]] = {}
    for consumer_path in listed_consumer_paths:
        consumer_pom = consumer_path / "pom.xml"
        dependencies = direct_simple_secret_dependencies(consumer_pom)
        if len(dependencies) != 1:
            errors.append(
                f"{consumer_path.name} must declare exactly one direct com.ss dependency; "
                f"found {len(dependencies)}"
            )
            continue
        dependency = dependencies.pop()
        artifact_consumers.setdefault(dependency, []).append(consumer_path.name)

    covered_artifacts = set(artifact_consumers)
    errors.extend(
        describe_difference(
            "Published module consumer coverage",
            publishable_artifacts,
            covered_artifacts,
        )
    )
    duplicate_targets = {
        artifact: consumers
        for artifact, consumers in artifact_consumers.items()
        if len(consumers) > 1
    }
    for artifact, consumers in sorted(duplicate_targets.items()):
        errors.append(
            f"Multiple consumers target {artifact}: {', '.join(sorted(consumers))}"
        )

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(
        "Published module coverage verified: "
        f"{len(publishable_artifacts)} artifacts, "
        f"{len(starter_artifacts)} starters, "
        f"{len(listed_consumer_paths)} consumers."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
