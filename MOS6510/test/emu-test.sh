#!/usr/bin/env bash
# mos-emu.sh [cases.txt] [out.txt]   (GHIDRA_INSTALL_DIR set)
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
CASES=$(readlink -f "${1:-$HERE/emu/cases.txt}"); OUT=$(readlink -m "${2:-${TMPDIR:-/tmp}/mos-emu.txt}")
PROJ=$(mktemp -d "${TMPDIR:-/tmp}/mos-emu.XXXXXX"); trap 'rm -rf "$PROJ"' EXIT
head -c 64 /dev/zero > "$PROJ/zero.bin"
"$GHIDRA_INSTALL_DIR/support/analyzeHeadless" "$PROJ" p -import "$PROJ/zero.bin" -loader BinaryLoader \
	-loader-baseAddr 0x1000 -processor 6510:LE:16:default -noanalysis -scriptPath "$HERE" \
	-postScript MosEmuTest.java "$CASES" "$OUT" -deleteProject >"$PROJ/log.txt" 2>&1 \
	|| { tail -40 "$PROJ/log.txt" >&2; exit 1; }
[[ -s $OUT ]] || { grep -E "ERROR|Exception" "$PROJ/log.txt" | head -20 >&2; exit 1; }
grep -v "^PASS " "$OUT" || true
grep -q " 0 failed, broken expectation detected" "$OUT"
