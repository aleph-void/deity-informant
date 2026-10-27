#!/usr/bin/env bash
# ss-run.sh <language-id> <tests.txt> <out.txt>   (GHIDRA_INSTALL_DIR set)
# Runs SsRun.java over the SingleStepTests data in chunks of 16 opcodes: Ghidra 12.2's PcodeEmulator keeps memory per
# emulator instance, and the 186,000 tests in one JVM exhaust the default heap there.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
LANG_ID=$1; IN=$(readlink -f "$2"); OUT=$(readlink -m "$3")
PROJ=$(mktemp -d "${TMPDIR:-/tmp}/mos-ss.XXXXXX"); trap 'rm -rf "$PROJ"' EXIT
python3 -I - "$IN" "$PROJ" <<'PY'
import sys
chunks = {}
for l in open(sys.argv[1]):
    op = int(l.split('|')[1], 16)
    chunks.setdefault(op // 16, []).append(l)
for k, v in chunks.items():
    open('%s/chunk%02d.txt' % (sys.argv[2], k), 'w').writelines(v)
PY
head -c 64 /dev/zero > "$PROJ/zero.bin"
: > "$OUT"
for c in "$PROJ"/chunk*.txt; do
	"$GHIDRA_INSTALL_DIR/support/analyzeHeadless" "$PROJ" p -import "$PROJ/zero.bin" -loader BinaryLoader \
		-loader-baseAddr 0x1000 -processor "$LANG_ID" -noanalysis -scriptPath "$HERE" \
		-postScript SsRun.java "$c" "$c.out" -overwrite >"$PROJ/log.txt" 2>&1 \
		|| { tail -40 "$PROJ/log.txt" >&2; exit 1; }
	[[ -s $c.out ]] || { grep -E "ERROR|Exception" "$PROJ/log.txt" | head -20 >&2; exit 1; }
	grep '^OP ' "$c.out" >> "$OUT"
done
