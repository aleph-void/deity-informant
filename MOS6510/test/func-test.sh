#!/usr/bin/env bash
# func-test.sh <image.bin> <success-pc> <max-steps> <out.txt>   (GHIDRA_INSTALL_DIR set)
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
IMG=$(readlink -f "$1"); OUT=$(readlink -m "$4")
PROJ=$(mktemp -d "${TMPDIR:-/tmp}/mos-func.XXXXXX"); trap 'rm -rf "$PROJ"' EXIT
head -c 64 /dev/zero > "$PROJ/zero.bin"
"$GHIDRA_INSTALL_DIR/support/analyzeHeadless" "$PROJ" p -import "$PROJ/zero.bin" -loader BinaryLoader \
	-loader-baseAddr 0x1000 -processor 6510:LE:16:default -noanalysis -scriptPath "$HERE" \
	-postScript FuncTest.java "$IMG" "$2" "$3" "$OUT" -deleteProject >"$PROJ/log.txt" 2>&1 \
	|| { tail -40 "$PROJ/log.txt" >&2; exit 1; }
cat "$OUT"
