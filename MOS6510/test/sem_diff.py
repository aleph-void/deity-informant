"""sem_diff.py <dir> : compares results.g.txt (Ghidra) with results.sim.txt (sim65) per test, buckets by opcode."""
import sys, collections
d = sys.argv[1]
tests = {}
for l in open(d + '/tests.txt'):
    f = l.split(' ')
    tests[f[0]] = (int(f[1], 16), f[2], int(f[3]))
g = dict(l.rstrip('\n').split(' ', 1) for l in open(d + '/results.g.txt'))
s = dict(l.rstrip('\n').split(' ', 1) for l in open(d + '/results.sim.txt'))
NAMES = ['A', 'X', 'Y', 'P', 'S']
bad = collections.defaultdict(list); okc = collections.Counter(); tot = collections.Counter()
for i, (op, mode, wrap) in tests.items():
    tot[op] += 1
    gh, sh = g.get(i, ''), s.get(i, '')
    if gh.startswith('ERR') or not gh or not sh:
        bad[op].append((i, 'ERROR', gh[:60] if gh else '-', sh[:20])); continue
    gb, sb = bytes.fromhex(gh), bytes.fromhex(sh)
    diffs = []
    for k in range(0x506):
        if gb[k] != sb[k] and k not in (0xfc, 0xfd):
            if 0x500 <= k < 0x505:
                n = NAMES[k - 0x500]
                if n == 'P' and (gb[k] & 0xcf) == (sb[k] & 0xcf):
                    continue
                diffs.append('%s g=%02x s=%02x' % (n, gb[k], sb[k]))
            else:
                diffs.append('mem%04x g=%02x s=%02x' % (k, gb[k], sb[k]))
    if diffs: bad[op].append((i, mode, wrap, diffs[:4]))
    else: okc[op] += 1
tp = sum(okc.values()); tt = sum(tot.values())
print('tests %d, identical %d, differing %d (opcodes with any difference: %d)' % (tt, tp, tt - tp, len(bad)))
for op in sorted(bad):
    print('%02x %-5s %d/%d differ; e.g. %s' % (op, tests[bad[op][0][0]][1], len(bad[op]), tot[op], bad[op][0]))
sys.exit(1 if tt - tp else 0)
