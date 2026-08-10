#!/bin/sh

set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
MAVEN_REPO=$(mktemp -d "${TMPDIR:-/tmp}/simple-secret-consumers.XXXXXX")

cleanup() {
    case "$MAVEN_REPO" in
        */simple-secret-consumers.*)
            rm -rf -- "$MAVEN_REPO"
            ;;
        *)
            echo "Refusing to remove unexpected temporary path: $MAVEN_REPO" >&2
            ;;
    esac
}

trap cleanup EXIT HUP INT TERM

cd "$PROJECT_ROOT"

mvn \
    -Dmaven.repo.local="$MAVEN_REPO" \
    -DskipTests \
    -Dmaven.javadoc.skip=true \
    -Dmaven.source.skip=true \
    install

mvn \
    -Dmaven.repo.local="$MAVEN_REPO" \
    -f integration-tests/pom.xml \
    test
