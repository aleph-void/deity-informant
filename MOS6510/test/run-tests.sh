#!/usr/bin/env bash
# Runs every MOS6510 check that needs no non-redistributable input.
#
#   GHIDRA_INSTALL_DIR=<Ghidra with this module linked into Ghidra/Processors/MOS6510, plus its built
#                       lib/MOS6510.jar for the PRG loader>   (unidasm from MAME on PATH)
#     test/run-tests.sh [--real] [--long] <workdir>
#
# Reference tools, all black boxes: MAME unidasm (m6510), cc65's da65 (6502X) and sim65 (6502X; built from a pinned source
# archive by fetch.sh), and the SingleStepTests/65x02 data.  --real adds the C64 programs built from cc65's samples, the Open ROMs
# KERNAL/BASIC and Klaus Dormann's functional test through headless import; --long also runs that functional test in the emulator
# (30.6 million instructions, a few minutes).  Exit status 0 only if every step passes.
set -uo pipefail
: "${GHIDRA_INSTALL_DIR:?set GHIDRA_INSTALL_DIR}"
HERE=$(cd "$(dirname "$0")" && pwd)
MOD=$(dirname "$HERE")
REAL=0; LONG=0
while [[ ${1:-} == --* ]]; do case $1 in --real) REAL=1; shift ;; --long) LONG=1; shift ;; *) echo "unknown option $1" >&2; exit 2 ;; esac; done
W=$(mkdir -p "$1" && cd "$1" && pwd)
export TMPDIR=${TMPDIR:-/tmp}
LID=6510:LE:16:default
fail=0
step() { echo "== $*"; }
ok() { "$@" || { echo "FAILED: $*"; fail=1; }; }

step "generated SMC overlay files are current"
ok python3 -I "$MOD/tools/gen_module.py" --check

step "spec compiles with no unresolved pattern conflicts (sleigh -l)"
cp -r "$MOD/data/languages" "$W/lang-compile"
out=$("$GHIDRA_INSTALL_DIR/support/sleigh" -l "$W/lang-compile/6510.slaspec" 2>&1)
if grep -q "ERROR" <<<"$out"; then echo "$out" | grep ERROR | head; echo "FAILED: compile"; fail=1; else echo "6510.slaspec compiles"; fi
ls -l "$W/lang-compile/6510.sla" | awk '{print "6510.sla " $5 " bytes"}'
rm -rf "$W/lang-compile"

step "fetch reference tools and data"
if [[ $REAL == 1 ]]; then ok "$HERE/fetch.sh" --real "$W"; else ok "$HERE/fetch.sh" "$W"; fi
export CC65_BIN=$W/cc65/bin

step "decode vs unidasm (m6510) and da65 (6502X): 256 opcodes x 8 operand pairs + 40,000 random instructions"
ok python3 -I "$HERE/gen_decode_cases.py" "$W/dec"
ok python3 -I "$HERE/oracle_dump.py" "$W/dec/cases.txt" "$W/dec/unidasm.txt" "$W/dec/da65.txt"
ok "$HERE/dis.sh" $LID "$W/dec/cases.txt" "$W/dec/g.txt"
ok python3 -I "$HERE/diff_decode.py" "$W/dec/g.txt" "$W/dec/unidasm.txt" "$W/dec/da65.txt"
step "decode check can fail (a corrupted result must be reported)"
sed '1s/^\([0-9a-f]* [0-9]* \)[A-Z]*/\1XXX/' "$W/dec/g.txt" > "$W/dec/g-broken.txt"
if python3 -I "$HERE/diff_decode.py" "$W/dec/g-broken.txt" "$W/dec/unidasm.txt" "$W/dec/da65.txt" >/dev/null 2>&1; then
	echo "FAILED: the corrupted result was not detected"; fail=1; else echo "corrupted result detected"; fi

step "every constructor is reached (default context and the four SMC contexts)"
out=$("$HERE/every-entry.sh" $LID "$W/dec/ee_cases.txt" "$W/ee.txt" | tail -1)
echo "$out"; grep -q " 0 not reached" <<<"$out" || { echo FAILED; fail=1; }

step "p-code: hand-computed emulator cases (includes a deliberately wrong expectation)"
ok "$HERE/emu-test.sh" "$HERE/emu/cases.txt" "$W/emu.txt"

step "SMC overlay: operand read from the code bytes (static p-code) and control flow (emulator)"
ok "$HERE/smc-check.sh" "$W/smc.txt"
ok "$HERE/smc-ctrl.sh" "$W/smcctrl.txt"

step "p-code vs sim65 (6502X): single instructions from random states, every opcode but branches/jumps/BRK/RTI/JAM, plain and SMC contexts"
ok python3 -I "$HERE/gen_sem.py" "$W/sem" --per-op 30
ok python3 -I "$HERE/run_sim65.py" "$W/sem" 6502X
for ctx in "" "smc_imm smc_addr" "smc_var"; do
	echo "-- context: ${ctx:-none}"
	ok "$HERE/sem-run.sh" $LID "$W/sem/tests.txt" "$W/sem/results.g.txt" $ctx
	ok python3 -I "$HERE/sem_diff.py" "$W/sem"
done
step "semantic check can fail (a corrupted result must be reported)"
sed '3s/^\(0*[0-9]* [0-9a-f]\{4\}\)/\100/' "$W/sem/results.g.txt" > "$W/sem/results.g.txt.bad"
cp "$W/sem/results.g.txt" "$W/sem/results.g.txt.good"; cp "$W/sem/results.g.txt.bad" "$W/sem/results.g.txt"
if python3 -I "$HERE/sem_diff.py" "$W/sem" >/dev/null 2>&1; then echo "FAILED: the corrupted result was not detected"; fail=1; else echo "corrupted result detected"; fi
cp "$W/sem/results.g.txt.good" "$W/sem/results.g.txt"

step "whole program with every undocumented opcode group (assembled with ca65): Ghidra emulation vs sim65, 140-byte log"
ok "$W/cc65/bin/ca65" --cpu 6502X -o "$W/illegal.o" "$HERE/prog/illegal.s"
ok "$W/cc65/bin/ld65" -C "$HERE/prog/illegal.cfg" -o "$W/illegal.bin" "$W/illegal.o"
ok python3 -I "$HERE/gen_prog.py" "$W/illegal.bin" "$W/prog"
ok python3 -I "$HERE/run_sim65.py" "$W/prog" 6502X
ok "$HERE/sem-run.sh" $LID "$W/prog/tests.txt" "$W/prog/results.g.txt"
ok python3 -I "$HERE/sem_diff.py" "$W/prog"

step "SingleStepTests/65x02 (MIT; hardware-derived single-instruction tests incl. decimal mode and undocumented opcodes)"
ok python3 -I "$HERE/ss_convert.py" "$W/ss" 100000 "$W/ss/tests.txt"
ok "$HERE/ss-run.sh" $LID "$W/ss/tests.txt" "$W/ss/results.txt"
python3 -I - "$W/ss/results.txt" <<'PY' || fail=1
import sys, re
JAM = {'%02x' % o for o in (0x02, 0x12, 0x22, 0x32, 0x42, 0x52, 0x62, 0x72, 0x92, 0xb2, 0xd2, 0xf2)}
tot = [0, 0]; jam = 0; edge = 0; other = []
for l in open(sys.argv[1]):
    m = re.match(r'OP (\w\w) pass (\d+) fail (\d+)(.*)', l)
    if not m: continue
    op, p, f, rest = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4)
    tot[0] += p; tot[1] += f
    if f and op in JAM: jam += f
    elif f and 'Unknown disassembly error' in rest: edge += f
    elif f: other.append(l.strip())
print('passed %d, failed %d: %d JAM (the lock-up leaves PC on the opcode; the test expects PC+1), %d instructions straddling $FFFF (Ghidra cannot fetch past the end of the address space), %d other' % (tot[0], tot[1], jam, edge, len(other)))
for o in other: print('  ', o)
sys.exit(1 if other else 0)
PY

if ((REAL)); then
	step "real code: C64 programs built from cc65's sources (auto-import with the PRG loader), Open ROMs KERNAL/BASIC"
	R=$W/real
	for p in $R/prg/*.prg; do
		n=$(basename "$p" .prg)
		echo -n "$n: "; ok "$HERE/real-prg.sh" "$p" "$R/prg_$n" | tail -1
		ok python3 -I "$HERE/real_compare.py" "$R/prg_$n.ins"
	done
	echo -n "Open ROMs KERNAL+BASIC (one image): "
	ok "$HERE/real-one.sh" "$R/c64rom.bin" 0xA000 "$R/c64rom" $LID ptr:A000 ptr:A002 ptr:FFFA ptr:FFFC ptr:FFFE | tail -1
	ok python3 -I "$HERE/real_compare.py" "$R/c64rom.ins"
	if ((LONG)); then
		step "Klaus Dormann's 6502 functional test in the p-code emulator (success = PC stuck at \$3469)"
		ok "$HERE/func-test.sh" "$R/6502_functional_test.bin" 3469 60000000 "$W/func.out"
		grep -q "^SUCCESS" "$W/func.out" || fail=1
	fi
fi
((fail)) && { echo "SOME CHECKS FAILED"; exit 1; }
echo "ALL CHECKS PASSED"
