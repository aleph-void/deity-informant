#!/usr/bin/env bash
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(readlink -m "$1")
PROJ=$(mktemp -d "${TMPDIR:-/tmp}/mos-smc.XXXXXX"); trap 'rm -rf "$PROJ"' EXIT
head -c 64 /dev/zero > "$PROJ/zero.bin"
"$GHIDRA_INSTALL_DIR/support/analyzeHeadless" "$PROJ" p -import "$PROJ/zero.bin" -loader BinaryLoader \
	-loader-baseAddr 0x1000 -processor 6510:LE:16:default -noanalysis -scriptPath "$HERE" \
	-postScript SmcCtrl.java "$OUT" -deleteProject >"$PROJ/log.txt" 2>&1 || { tail -40 "$PROJ/log.txt" >&2; exit 1; }
grep FAIL "$OUT" || true
tail -1 "$OUT"
grep -q " 0 failed" "$OUT"
