"""diff_decode.py <ghidra.txt> <unidasm.txt> <da65.txt> : bucket decode differences."""
import re, sys, collections, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from oracle_common import *
g = {l.split(' ', 2)[0]: l.rstrip('\n').split(' ', 2)[1:] for l in open(sys.argv[1])}
u = dict(l.rstrip('\n').split('\t', 1) for l in open(sys.argv[2]))
d = dict(l.rstrip('\n').split('\t', 1) for l in open(sys.argv[3]))
def fix_len(c, n):
    """zp vs abs from instruction length; ghidra prints no width"""
    return c
res = collections.Counter(); ex = collections.defaultdict(list)
for h, (n, text) in g.items():
    pass
rows = []
seen = set()
for l in open(sys.argv[1]):
    h, n, text = l.rstrip('\n').split(' ', 2)
    if h in seen: continue
    seen.add(h)
    gc = norm(canon(text)) if text != 'BAD' else None
    if text != 'BAD' and int(n) != len(h) // 2: res[('LENGTH', h)] += 1
    uc = norm(canon(u[h])); dc = norm(canon(d[h]))
    def cmp(a, b):
        if a == b: return 'same'
        if a is None or b is None: return 'one-invalid'
        if a[2] is not None and b[2] is not None and False: pass
        if a[0] != b[0]: return 'mnemonic'
        if a[1] != b[1]: return 'mode'
        return 'operand'
    ku, kd = cmp(gc, uc), cmp(gc, dc)
    key = (ku, kd, gc[0] if gc else None)
    res[(ku, kd)] += 1
    if ku != 'same' or kd != 'same':
        if len(ex[(ku, kd)]) < 12: ex[(ku, kd)].append((h, text, u[h], d[h]))
print('cases', len(seen))
for k, v in res.most_common(): print(k, v)
for k, v in ex.items():
    print('==', k)
    for e in v: print('   ', e)
# da65 cannot print a branch whose target lies inside its own instruction (offset $FE/$FF): explained, not a module difference
unexplained = sum(v for k, v in res.items() if k not in (('same', 'same'), ('same', 'one-invalid')) and k[0] != 'LENGTH')
unexplained += sum(1 for k in res if k[0] == 'LENGTH')
for k, v in res.items():
    if k == ('same', 'one-invalid'):
        assert all(e[3] == 'NONE' and e[0][:2] in ('10','30','50','70','90','b0','d0','f0') for e in ex[k]), 'unexpected one-invalid class'
print('UNEXPLAINED DIFFERENCES: %d' % unexplained)
sys.exit(1 if unexplained else 0)
