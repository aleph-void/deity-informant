# Credits: MOS6510

## Upstream

- Repository: <https://github.com/anarkiwi/deity-informant>
- Commit imported: `deb3466c1481d9edd0f0b6c325e6188bbe9257ca` ("Release 0.6.0: deity_informant.c64 removed; the machine model is vagrant-moses (#363)", 2026-10-15)
- License: Apache-2.0 (`LICENSE`, unchanged)
- Original authors (from `git log`, 139 commits): **Josh Bailey** (`anarkiwi`, 138 commits) and **CBongo** (1 commit, `88fea34`
  "Correct 6510 SLEIGH arithmetic and indirect addressing (#359)": the NMOS ADC/SBC and zero-page pointer routines this module
  reuses for the legal instructions).
- Upstream describes the undocumented-opcode semantics as following "No More Secrets - NMOS 6510 Unintended Opcodes" (v0.91).

## Other sources inside the module

- `data/languages/6502_base.sinc` starts from Ghidra's stock `6502.slaspec` (Apache-2.0, National Security Agency / Ghidra
  contributors; byte-identical in Ghidra 12.1.3, 12.1.4 and 12.2, SHA-256
  `d1a81dec7ac95814bd6636a4db27c07c38d3469bf0dd69ff924e56dd15b7d50d`), with deity-informant's patches (hardware JSR/RTS return
  address, SBC carry) and the Ghidra-Rare changes below.
- Nothing was taken from GPL sources (binutils tables, GPL emulators) or from MAME's source: MAME `unidasm`, cc65's `da65` and
  `sim65`, and the SingleStepTests data were used only as black boxes / test inputs.

## What Ghidra-Rare changed (commits on branch `ghidra-rare`, on top of `deb3466`)

1. `c0b0d97` -- self-contained module directory `MOS6510/` (vendored stock 6502 base, committed generated files, build files);
   fixed 24 overlapping constructor pairs in the SMC overlay generator (`smc.py`).
2. `dcbfc30` -- hardware-accurate legal instructions found wrong by differential testing: NMOS ADC/SBC (V flag, decimal mode),
   zero-page pointer wrap for `(zp,X)`/`(zp),Y`, `JMP (abs)` page bug, PHP/BRK push B=1, BRK pushes PC+2, stack pointer wraps
   inside page 1, SHA/SHX/SHY/TAS page-cross address corruption; SMC overlay corrections (RTS guard, JSR return address, stack).
3. `b98e331` -- C64 symbols, volatile `$00/$01`, tracked stack page in `6510.pspec`; `C64PrgLoader`.
4. the "Add Ghidra-Rare notes, credits, tests" commit -- `GHIDRA-RARE.md`, this file, `test/`, README updates.

## Test inputs (fetched by `test/fetch.sh`, never committed)

| Input | License | Pin |
|---|---|---|
| cc65 2.19 (da65, sim65, ca65, cl65, sample programs) | zlib | commit `5725066`, archive SHA-256 `56100cea...cca1b8` |
| SingleStepTests/65x02 `6502/v1` | MIT | commit `2f6980a`, prefix set SHA-256 `a5a03cd2...6584f` |
| Open ROMs `kernal_generic.rom`, `basic_generic.rom` (MEGA65/open-roms) | LGPL-3.0 | commit `ad178db` |
| Klaus Dormann's `6502_functional_test.bin` | GPL-3.0 | commit `7954e2d` |
| MAME `unidasm` (`-arch m6510`) | external tool | any recent MAME |
