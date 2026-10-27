# deity-informant

NMOS 6510 toolkit: a 6510 -> raw-P-Code lifter + pure-Python P-Code VM (all 105 documented illegal opcodes are first-class), and a `6510` Ghidra/pypcode SLEIGH processor module. The `jennings` package supplies the opcode table, assembler and disassembler, and its py65-compatible `MPU` executes on this lifter + VM.

SID tune decompilation (tuneprog, trackerprog) built on this toolkit lives in [anarkiwi/trackerprog](https://github.com/anarkiwi/trackerprog); the C64 machine model (power-on RAM, interrupt-vector discovery, ROM-free IRQ dispatch) in [anarkiwi/vagrant-moses](https://github.com/anarkiwi/vagrant-moses).

## Components

- `deity_informant/lifter.py` — `lift(mem, pc)` -> `{"ops", "len", "cyc", "pen", "ctrl", "prov", "stk"}` raw P-Code; `CYCLETIME`/`EXTRACYCLES` tables.
- `deity_informant/vm.py` — `PcodeVM` interpreter over a flat 64 KiB image (SID/VIC/CIA volatile IO modeled); `run_sub`/`run_irq`/`run_irq_driven` drivers.
- `deity_informant/cli.py` — `deity-informant` console script.
- `deity_informant/sleigh/` — SLEIGH module (`languages/6510.slaspec` = stock 6502 + `6510_illegal.sinc`), `smc.py` (SMC context constructors), `build.py`, and `pypcode_context()` (a loaded pypcode `Context` for the 6510).
- `ghidra/6510/headless/` — headless Ghidra integration test, run via `Dockerfile.ghidra`.
- `deity_informant/hello_world.py` — 33-byte C64 program using `LAX`/`ISC` + self-modifying code; the fixture for the VM and Ghidra tests.
- `tools/doclinks.py` — every `[text](target)` in `docs/` and `README.md` resolved: the file, and the `#anchor` against the target's own headings.

## Install

```bash
pip install deity-informant         # from PyPI; the SLEIGH sources ship as package data
pip install -e ".[dev]"             # from a checkout: + test/lint tooling, py65, pypcode
```

## CLI

```bash
deity-informant disasm IMAGE [--org ADDR] [--start ADDR] [--count N]   # illegal-aware disassembly
deity-informant pcode  IMAGE --at ADDR [--org ADDR]                    # raw P-Code for one instruction
deity-informant run    IMAGE --init ADDR [--play ADDR --frames N]      # execute in PcodeVM, dump $D400.. grid
deity-informant emit-sleigh [-o DIR] [--magic 0xEE]                    # build/install the 6510 SLEIGH module
```

## Python API

```python
from deity_informant import lift, PcodeVM, run_sub
vm = PcodeVM(mem); run_sub(vm, entry, {}, lift)   # execute a subroutine to its RTS
```

## Ghidra

**Ghidra-Rare:** the Ghidra processor module is now the self-contained directory [`MOS6510/`](MOS6510/) (a normal Ghidra
extension: install the zip from this repository's Releases page, or link the directory into `Ghidra/Processors/`). It needs no
`emit-sleigh` step, compiles with Ghidra 12.1.3, 12.1.4 and 12.2, and carries hardware-accuracy fixes and a C64 PRG loader; see
[`MOS6510/GHIDRA-RARE.md`](MOS6510/GHIDRA-RARE.md) for what was verified and what is still open, and
[`MOS6510/CREDITS.md`](MOS6510/CREDITS.md) for provenance. Language id: `6510:LE:16:default`. `.prg` files import with the
"Commodore 64 PRG" loader; raw ROM images import as Raw Binary at their load address.

The original Python route below still builds the upstream (pre-Ghidra-Rare) spec from Ghidra's or pypcode's stock `6502.slaspec`
for pypcode use; it does not include the Ghidra-Rare fixes (NMOS ADC/SBC, stack-page wrap, `JMP (abs)` page bug, PHP/BRK, SHA/SHX/SHY
page-crossing) -- use `MOS6510/data/languages` for those.

```bash
deity-informant emit-sleigh -o "$GHIDRA_INSTALL_DIR/Ghidra/Processors/6510/data/languages"
```

Resolves the stock `6502.slaspec` + SLEIGH compiler from `$GHIDRA_INSTALL_DIR` or a `pypcode` install; the stock spec's `JSR`/`RTS` return address and `SBC` borrow are patched to the hardware's. `docker build -f Dockerfile.ghidra -t di-ghidra . && docker run --rm di-ghidra` runs the headless integration test.

### Known limitations (status after Ghidra-Rare verification)

| Limitation | Status |
|---|---|
| The committed `6510.slaspec` did not compile (it needed a generated base and SMC files) | Fixed: `MOS6510/` compiles as committed |
| SMC overlay: overlapping constructors; RTS guard / JSR / stack wrong | Fixed (see `MOS6510/CREDITS.md`) |
| Stock 6502 ADC (V flag, no decimal mode), pointer wrap, `JMP (abs)` bug, PHP/BRK B bit, BRK return address | Fixed in the module |
| Stack pointer wrapped out of page 1 (16-bit SP arithmetic) | Fixed: byte-S arithmetic |
| SHA/SHX/SHY/TAS ignored the page-cross address corruption | Fixed; RDY/DMA instability not modelled |
| ANE/LXA/SHA... constants are chip dependent (`$EE` used) | Remaining: inherent; `sleigh -DMAGIC=` overrides |
| 6510 port `$00/$01` bank switching of ROM/I-O | Remaining: Ghidra's address map is static; names and volatile flags only |
| JAM leaves PC on the opcode (hardware: PC+1, then locked) | Remaining: unobservable by programs |
| Python build path lacks the fixes | Remaining: use `MOS6510/` for Ghidra |

## Tests

```bash
black --check deity_informant/ tests/ && pylint deity_informant/
deity-informant emit-sleigh
pytest tests/ -n auto --cov=deity_informant --cov-fail-under=85
```

## References

Illegal-opcode semantics and cycles: "No More Secrets — NMOS 6510 Unintended Opcodes" ([csdb.dk/release/?id=258111](https://csdb.dk/release/?id=258111)).
