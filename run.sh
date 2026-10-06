#!/usr/bin/env bash
# Compiles and launches the event management system.
#
# Needs only a JDK 17 or newer and the bundled H2 driver. There is no build tool,
# because there is nothing to depend on: the application is plain Java and Swing.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$ROOT_DIR/build/classes"
LIB_DIR="$ROOT_DIR/lib"
cd "$ROOT_DIR"

# The application is a Swing window, so it needs an X display. A terminal opened by
# hand normally has DISPLAY already set, but some launchers do not pass it on, and
# Java's answer to a missing display is a HeadlessException with no window and no
# explanation. Look for one rather than leaving the user to read a stack trace: the
# first X socket in /tmp/.X11-unix is the display already in use.
if [[ -z "${DISPLAY:-}" ]]; then
  for socket in /tmp/.X11-unix/X*; do
    if [[ -e "$socket" ]]; then
      export DISPLAY=":${socket##*/X}"
      break
    fi
  done
fi

if [[ -z "${DISPLAY:-}" ]]; then
  echo "No display found, so the window cannot open."
  echo
  echo "This program is a desktop application and needs a running graphical session."
  echo "Either:"
  echo "  - start it from the desktop rather than a bare terminal, or"
  echo "  - if you are on a remote machine, forward X11 first:"
  echo "        ssh -X user@host"
  exit 1
fi

mkdir -p "$OUT_DIR"
find "$ROOT_DIR/src/main/java" -name '*.java' -print0 \
  | xargs -0 javac -nowarn -cp "$LIB_DIR/*" -d "$OUT_DIR"

# A different file name can be passed as the first argument, which is how two sets of
# events are kept side by side without either being overwritten.
exec java -cp "$OUT_DIR:$LIB_DIR/*" com.eventsuite.App "$@"
