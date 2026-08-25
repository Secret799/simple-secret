#!/usr/bin/env python3
"""Set the Maven CI-friendly revision property in a pom.xml file."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0"


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "Usage: set-pom-revision.py <pom.xml> <revision>",
            file=sys.stderr,
        )
        return 2

    pom_path = Path(sys.argv[1])
    revision_value = sys.argv[2].strip()
    if not revision_value:
        print("Revision must not be empty", file=sys.stderr)
        return 2

    tree = ET.parse(pom_path)
    root = tree.getroot()
    revision = root.find(
        f"{{{MAVEN_NAMESPACE}}}properties/"
        f"{{{MAVEN_NAMESPACE}}}revision"
    )
    if revision is None:
        print(f"Missing properties/revision in {pom_path}", file=sys.stderr)
        return 1

    revision.text = revision_value
    ET.register_namespace("", MAVEN_NAMESPACE)
    tree.write(pom_path, encoding="UTF-8", xml_declaration=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
