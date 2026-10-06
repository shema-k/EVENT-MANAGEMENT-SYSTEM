#!/usr/bin/env bash
# Compiles and runs the test suite. Needs only a JDK and the bundled H2 driver.
#
# There is no build tool and no test framework: compile the sources and run a single
# class, which is all a desktop application of this size needs.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$ROOT_DIR/build/test-classes"
LIB_DIR="$ROOT_DIR/lib"
cd "$ROOT_DIR"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

find "$ROOT_DIR/src/main/java" -name '*.java' -print0 \
  | xargs -0 javac -nowarn -cp "$LIB_DIR/*" -d "$OUT_DIR"
find "$ROOT_DIR/src/test/java" -name '*.java' -print0 \
  | xargs -0 javac -nowarn -cp "$OUT_DIR:$LIB_DIR/*" -d "$OUT_DIR"

java -cp "$OUT_DIR:$LIB_DIR/*" com.eventsuite.TestRunner
