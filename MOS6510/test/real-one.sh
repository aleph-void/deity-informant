#!/usr/bin/env bash
# real-one.sh <binary> <base-addr-hex> <outprefix> [language]  : headless import (analysis on) + RealCheck
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
BIN=$(readlink -f "$1"); BASE=$2; PFX=$(readlink -m "$3"); LANG_ID=${4:-6510:LE:16:default}; shift 4 2>/dev/null || shift $#; SEEDS=("$@")
PROJ=$(mktemp -d "${TMPDIR:-/tmp}/mos-real.XXXXXX"); trap 'rm -rf "$PROJ"' EXIT
"$GHIDRA_INSTALL_DIR/support/analyzeHeadless" "$PROJ" p -import "$BIN" -loader BinaryLoader \
	-loader-baseAddr "$BASE" -processor "$LANG_ID" -scriptPath "$HERE" \
	${SEEDS[@]:+-preScript Seed.java "${SEEDS[@]}"} \
	-postScript RealCheck.java "$PFX.summary" "$PFX.ins" 30 -deleteProject >"$PROJ/log.txt" 2>&1 \
	|| { tail -40 "$PROJ/log.txt" >&2; exit 1; }
cat "$PFX.summary"
