#!/usr/bin/env bash
# Fetches (pinned URL + SHA-256) and prepares everything the tests need that is not committed:
#   test/fetch.sh [--real] <workdir>
# Always: cc65 2.19 (zlib license) source at a pinned commit, built into <workdir>/cc65 (bin: da65, sim65, ca65, ld65, cl65 are the
#   reference tools, used as black boxes), plus the SingleStepTests/65x02 6502/v1 data (MIT; the first ~600 tests of each opcode).
# With --real: the C64 libraries of cc65 and the sample programs built from its sources, the Open ROMs C64 KERNAL/BASIC (LGPL-3.0)
#   and Klaus Dormann's 6502 functional test (GPL-3.0).  Nothing fetched or built here is ever committed.
# MAME's `unidasm` must be on PATH (package mame-tools or a MAME build); it is the second reference disassembler.
set -euo pipefail
REAL=0; [[ ${1:-} == --real ]] && { REAL=1; shift; }
W=$(mkdir -p "$1" && cd "$1" && pwd)
CC65_SHA=5725066bfb86cdb52c8f4afb94b6401cb320cb21
CC65_TGZ_SHA256=56100ceaa5a01fa3e1da896495bbff87b38416154bc3d57a4605d2d048cca1b8
SS_SHA=2f6980a2d95757486c7bee24355c360e40e2a224
ORM_SHA=ad178dbe4d48cd6a317737a8e0e7e662f7e33d32
KD_SHA=7954e2dbb49c469ea286070bf46cdd71aeb29e4b
get() { # url sha256 out
	if [[ -f $3 ]] && echo "$2  $3" | sha256sum -c --quiet 2>/dev/null; then return; fi
	curl -sfL -o "$3" "$1"
	echo "$2  $3" | sha256sum -c --quiet || { echo "checksum mismatch: $3" >&2; exit 1; }
}
mkdir -p "$W/dl"
if [[ ! -x $W/cc65/bin/sim65 ]]; then
	get "https://github.com/cc65/cc65/archive/$CC65_SHA.tar.gz" $CC65_TGZ_SHA256 "$W/dl/cc65.tar.gz"
	mkdir -p "$W/cc65" && tar -xzf "$W/dl/cc65.tar.gz" -C "$W/cc65" --strip-components=1
	make -C "$W/cc65" -j8 bin >"$W/cc65/build.log" 2>&1
fi
if [[ ! -f $W/ss/DONE ]]; then
	mkdir -p "$W/ss"
	for i in $(seq 0 255); do
		h=$(printf %02x "$i")
		curl -sf -r 0-299999 -o "$W/ss/$h.json" "https://raw.githubusercontent.com/SingleStepTests/65x02/$SS_SHA/6502/v1/$h.json" &
		(( i % 16 == 15 )) && wait
	done
	wait
	# SHA-256 of the 256 prefixes together, in opcode order
	got=$(cat "$W"/ss/??.json | sha256sum | cut -d' ' -f1)
	[[ $got == a5a03cd220804d2471cbf3c6ac1c550d263e9ce37f81128b2f4ab18649b6584f ]] || { echo "checksum mismatch: SingleStepTests prefixes" >&2; exit 1; }
	touch "$W/ss/DONE"
fi
((REAL)) || exit 0
mkdir -p "$W/real"
if [[ ! -f $W/cc65/lib/c64.lib ]]; then
	mkdir -p "$W/cc65/lib"
	make -C "$W/cc65/libsrc" c64 -j8 >"$W/cc65/libbuild.log" 2>&1
fi
export CC65_HOME=$W/cc65
mkdir -p "$W/real/prg"
for p in hello sieve mandelbrot gunzip65 checkversion ascii tgidemo; do
	[[ -f $W/real/prg/$p.prg ]] || "$W/cc65/bin/cl65" -t c64 -O -o "$W/real/prg/$p.prg" "$W/cc65/samples/$p.c"
done
get "https://raw.githubusercontent.com/MEGA65/open-roms/$ORM_SHA/bin/kernal_generic.rom" 88e86ed3d0c710edab8f90ad146faa8de1ead11f43494b176c7b54724ca721c6 "$W/real/kernal_generic.rom"
get "https://raw.githubusercontent.com/MEGA65/open-roms/$ORM_SHA/bin/basic_generic.rom" 54a1464b4b27c9dc61bbd62a818fdd12ec99af9089111005a5add0ad0e6bd5ec "$W/real/basic_generic.rom"
get "https://raw.githubusercontent.com/Klaus2m5/6502_65C02_functional_tests/$KD_SHA/bin_files/6502_functional_test.bin" fa12bfc761e6f9057e4cc01a665a7b800ff01ae91f598af1e39a1201d01953fd "$W/real/6502_functional_test.bin"
# BASIC at $A000, 8 KiB of zeros where the I/O area would be, KERNAL at $E000: one image of the ROM set
python3 -I -c "
import sys
b = open(sys.argv[1], 'rb').read(); k = open(sys.argv[2], 'rb').read()
open(sys.argv[3], 'wb').write(b + bytes(0x2000) + k)" "$W/real/basic_generic.rom" "$W/real/kernal_generic.rom" "$W/real/c64rom.bin"
