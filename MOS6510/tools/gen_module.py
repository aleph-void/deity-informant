#!/usr/bin/env python3
"""Regenerate the two generated SLEIGH files of the module: data/languages/6510_context.sinc and 6510_smc.sinc.

  gen_module.py [--check]

6510_smc.sinc is derived from 6510_illegal.sinc by deity_informant/sleigh/smc.py (the self-modifying-code
overlay).  With --check nothing is written; exit status 1 if a committed file differs from the generator output.
Everything else under data/languages is maintained by hand.
"""
import sys, types, pathlib

root = pathlib.Path(__file__).resolve().parents[2]
pkg = root / "deity_informant"
# deity_informant/__init__.py imports the Python VM (needs the 'jennings' package); smc.py does not.
for name, path in (("deity_informant", pkg), ("deity_informant.sleigh", pkg / "sleigh")):
    m = types.ModuleType(name)
    m.__path__ = [str(path)]
    sys.modules[name] = m
sys.path.insert(0, str(root))
from deity_informant.sleigh.smc import CONTEXT, smc_sinc  # noqa: E402

LANG = pathlib.Path(__file__).resolve().parents[1] / "data" / "languages"
check = "--check" in sys.argv
files = {
    "6510_context.sinc": CONTEXT,
    "6510_smc.sinc": smc_sinc((LANG / "6510_illegal.sinc").read_text()),
}
bad = 0
for n, t in files.items():
    p = LANG / n
    if check:
        if not p.exists() or p.read_text() != t:
            print("differs:", n)
            bad = 1
    else:
        p.write_text(t)
sys.exit(bad)
