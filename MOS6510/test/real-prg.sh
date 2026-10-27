#!/usr/bin/env bash
# real-prg.sh <file.prg> <outprefix> : headless import WITHOUT forcing a language (the C64 PRG loader and the 6510 opinion pick it) + RealCheck
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
BIN=$(readlink -f "$1"); PFX=$(readlink -m "$2")
PROJ=$(mktemp -d "${TMPDIR:-/tmp}/mos-prg.XXXXXX"); trap 'rm -rf "$PROJ"' EXIT
"$GHIDRA_INSTALL_DIR/support/analyzeHeadless" "$PROJ" p -import "$BIN" -scriptPath "$HERE" \
	-postScript RealCheck.java "$PFX.summary" "$PFX.ins" 30 -deleteProject >"$PROJ/log.txt" 2>&1 \
	|| { tail -40 "$PROJ/log.txt" >&2; exit 1; }
grep -q "Using Loader: Commodore 64 PRG" "$PROJ/log.txt" || { echo "PRG loader was not selected" >&2; exit 1; }
cat "$PFX.summary"
