# MOS6510 (Commodore 64 CPU) -- Ghidra-Rare notes

Language id `6510:LE:16:default` (kept from upstream; Ghidra's own is `6502:LE:16:default`). Extension name `MOS6510`.
Provenance and licenses: [CREDITS.md](CREDITS.md). Upstream: [anarkiwi/deity-informant](https://github.com/anarkiwi/deity-informant),
module code by Josh Bailey, with a correction by CBongo; Apache-2.0.

## What this adds over Ghidra's 6502 module

Ghidra already ships `6502` and `65C02`. The 6510 is a 6502 core with an I/O port, and a C64 program uses things the stock module
does not have:

1. **The 105 undocumented NMOS opcodes** (LAX, SAX, SLO, RLA, SRE, RRA, DCP, ISC, ANC, ALR, ARR, SBX, ANE, LXA, LAS, SHA, SHX, SHY,
   TAS, the NOP variants, SBC `$EB`, JAM). The stock module reports them as bad data; C64 code (demos, copy protection, fast loaders)
   uses them. Semantics are in p-code and the decompiler sees them.
2. **NMOS behaviour the stock module gets wrong** (found by the tests below; a run of the stock `6502` language over the 151
   documented opcodes of the SingleStepTests data passes 100,528 of 113,059 tests, 88.9%; this module passes all of them except
   the 18 that straddle `$FFFF`):
   ADC V flag and decimal mode, SBC borrow and decimal mode, `JSR`/`RTS` return address (off by one), `BRK` return address and
   pushed status, `PHP` pushing B, `(zp,X)`/`(zp),Y` pointers at `$FF`, `JMP ($xxFF)`, and the stack pointer wrapping inside page 1.
3. **A self-modifying-code overlay** (upstream's feature, fixed here): context bits `smc_imm`, `smc_addr`, `smc_ctrl`, `smc_var`
   select constructors that read the operand from the code bytes at run time instead of the decoded constant. Set them on an
   address range (Set Register Values) to let the decompiler follow patched operands; they do not flow to the next instruction.
4. **C64 knowledge in the language**: names for the 6510 port (`CPU_DDR` `$00`, `CPU_PORT` `$01`, volatile), VIC-II, SID, CIA 1/2,
   colour RAM, the KERNAL jump table (entry points, so analysis starts there), the RAM vectors `$0314-$0333` and the BASIC start
   vectors; the stack page tracked as 1.
5. **A loader**: `.prg` files (two-byte load address) load at that address with the "Commodore 64 PRG" loader, the BASIC
   `SYS n` stub names the entry point, the rest of the ROM/I-O area becomes placeholder blocks (I/O volatile), and the KERNAL jump
   table is a block of RTS stubs so `JSR $FFD2` resolves to a function named `CHROUT` instead of a bad-instruction bookmark.

What it does not add: ROM/I-O **banking** by the port at `$01`. Ghidra's address map is static, so the usual configuration (I/O at
`$D000`, ROMs at `$A000`/`$E000`) is shown and the port is only named and marked volatile. Importing a second configuration means
importing the bytes at the same addresses as an overlay block by hand.

## How to test

```
GHIDRA_INSTALL_DIR=<Ghidra with this directory linked as Ghidra/Processors/MOS6510 and its built lib/MOS6510.jar> \
  test/run-tests.sh [--real] [--long] <workdir>
```

Needs MAME's `unidasm` on `PATH`. `fetch.sh` builds cc65 from a pinned archive (da65, sim65, ca65, cl65 are used as black boxes) and
fetches the other inputs by pinned URL and SHA-256 (nothing is committed). `--real` adds real code; `--long` runs Klaus Dormann's
functional test in the emulator (about 3 minutes). Exit status 0 only if every step passes; a corrupted decode result, a corrupted
semantic result and a deliberately wrong hand expectation must each be reported or the run fails.

## Results (Ghidra 12.1.4; 12.1.3 and 12.2 identical where re-run, see below)

| Area | Result |
|---|---|
| Build | `6510.slaspec` compiles with `sleigh -l` (no pattern conflicts) on 12.1.3, 12.1.4, 12.2; `6510.sla` 18 KB. Release zips built with `tools/build-module.sh --standard --name MOS6510` for all three |
| Decode | 28,854 distinct encodings (256 opcodes x 8 operand pairs, plus 40,000 random instructions) vs MAME `unidasm -arch m6510` and cc65 `da65 --cpu 6502X`: 28,838 identical to both after normalising mnemonic spellings; 16 only differ because `da65` prints nothing for a branch whose target lies inside the branch itself (offsets `$FE`/`$FF`). 0 unexplained |
| Spelling classes | `ISC`/isb, `JAM`/kil, `ALR`/asr (unidasm), `TAS`/shs (unidasm), `SBX`/axs (da65), `LXA`/lax (da65, immediate), `ANE`/xaa; all the same opcode |
| Every entry | 207/207 constructors reached (default context and the four SMC contexts) |
| Hand-computed p-code | 51/51 emulator cases (ALU and flags incl. decimal mode, pointer wraps, `JMP ($xxFF)`, JSR/RTS/BRK/RTI frames, PHP/PLP, stack wrap both ways, branches, 17 undocumented-instruction cases, SHY page cross); one deliberately wrong expectation is reported |
| vs sim65 (6502X) | 6,900 single-instruction runs from random states (230 opcodes x 30; all but branches, JMP/JSR/RTS/RTI/BRK and JAM): 0 differences, in the plain language and with the SMC contexts set (3 x 6,900) |
| Whole program | 35-step program with every undocumented opcode group (`test/prog/illegal.s`, assembled with ca65): memory and log identical to sim65 |
| SingleStepTests/65x02 `6502/v1` (MIT; first ~730 tests of each opcode = 186,529) | 179,744 pass. The 6,785 others: 6,767 JAM (the lock-up leaves PC on the opcode; the data expects PC+1) and 18 instructions whose bytes straddle `$FFFF` (Ghidra cannot fetch across the end of the address space). 0 other, undocumented opcodes, decimal mode, ANE/LXA/SHA... included |
| SMC overlay | 38/38 static p-code checks (operand loaded from instruction address + 1 only with the bit set); 34/34 control-flow cases (JMP, JSR, 8 branches x 2 outcomes x 2 directions); the 6,900 random runs above execute the overlay constructors |
| Klaus Dormann functional test (GPL-3.0, fetched) | runs to its success trap at `$3469` after 30,646,177 instructions in the p-code emulator (12.1.4) |
| Real code, 7 programs built from cc65 2.19 sample sources for the C64 (hello, sieve, mandelbrot, gunzip65, checkversion, ascii, tgidemo; auto-imported with the PRG loader) | per program below: 0 decompile failures, every instruction identical to both reference disassemblers |
| Real code, Open ROMs KERNAL + BASIC (LGPL-3.0) | 4,058 instructions, 275 functions (seeded from the BASIC and CPU vectors), 0 decompile failures; every instruction identical to unidasm and da65 |

Programs (instructions / functions / decompile failures / bad-instruction bookmarks): hello 1,053/72/0/4, sieve 1,202/61/0/2,
mandelbrot 2,832/155/0/5, gunzip65 2,338/121/0/3, checkversion 907/54/0/1, ascii 1,019/62/0/4, tgidemo 3,609/194/0/3. Every
instruction is identical to `unidasm` and `da65`. All bad-instruction bookmarks are explained: 20 are calls and jumps into KERNAL
internals that a PRG does not contain (for example `JSR $E544`) or cc65's self-modified `JSR $FFFF` placeholder, and 13 are in the
ROM images and `gunzip65` (11 + 2) where code jumps into the middle of another instruction -- the `$2C` (BIT abs) skip trick and
entry points inside a chain of `INC $E6`; Ghidra's instruction model cannot hold two instructions at overlapping addresses.

12.1.3 and 12.2: `sleigh -l`, every-entry, the 51 emulator cases, the sim65 comparison (3 x 6,900), the whole program and the
SingleStepTests data (179,744 pass) were re-run with the same numbers; the PRG import was re-run with each version's own
`MOS6510.jar`. Klaus Dormann's test and the Open ROMs imports were run on 12.1.4 only.

## Known gaps (and why)

- **Banking by `$01`** is not modelled: Ghidra's address map is static (see above).
- **Port side effects** (`$00/$01` bits 6-7 floating, bit 5 motor line, data-direction masking): the two bytes are plain volatile RAM.
- **ANE/LXA constants** are chip and temperature dependent; the module uses `$EE` (the value of the sim65 and SingleStepTests data, and
  of the original module). `sleigh -DMAGIC=0x00` rebuilds with another. SHA/SHX/SHY/TAS also drop the AND on DMA (RDY) cycles on
  real chips; that depends on cycle timing and is not modelled.
- **JAM** leaves PC on the opcode (an infinite loop); hardware stops with PC advanced by one. A program cannot observe the difference.
- **Interrupts**: IRQ/NMI entry (pushing P with B clear, vector fetch) is not an instruction and is not modelled; BRK is.
- **Cycle counts and timing** are not modelled (the upstream Python lifter has cycle tables; the Ghidra module does not).
- **SMC bits are set by hand**; there is no analyzer that infers them.
- **Python path**: upstream's `deity-informant emit-sleigh`/pypcode build still produces the pre-Ghidra-Rare spec (without the fixes
  above). Only the Ghidra module in this directory has them. The Python package's own test suite was not run (it needs `jennings`
  and `pypcode`, not installed here).
- **Instructions overlapping at different offsets** (the BIT-skip and INC-chain idioms) get bad-instruction bookmarks, as above.
- **No DWARF/ELF**: cc65 emits its own debug format, there are no ELF C64 binaries in practice, so no `.dwarf` mapping or ELF opinion.

## Unverified

- No freely licensed third-party C64 software that uses the undocumented opcodes was found; the undocumented instructions are
  verified by sim65, the SingleStepTests data (which includes them), the hand-computed cases and the assembled program, not by a
  real C64 program.
- No hardware or VICE comparison. VICE (`x64sc` monitor) is not installed here and could not be built without root; sim65 and the
  SingleStepTests data (a cycle-exact software model) took its place.
- ANE/LXA/SHA/SHX/SHY/TAS values on a real chip.

## Confirmed reference-tool behaviour

- `da65` prints nothing for a relative branch whose target lies inside the branch instruction itself (offset `$FE`/`$FF`).
- `da65 --cpu 6502X` spells opcode `$AB` `lax #imm`, `$CB` `axs`; `unidasm` spells `$EB` `sbc` (as Ghidra does), `$4B` `asr`, `$9B` `shs`.
- sim65 implements the SHA/SHX/SHY/TAS page-crossing address corruption; the original module did not (fixed).
- The SingleStepTests data expects a JAM to advance PC by one.
