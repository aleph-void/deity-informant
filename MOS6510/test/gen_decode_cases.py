"""gen_decode_cases.py <outdir> : decode corpus for the oracle diff and the every-entry test.
Writes oplen.txt (instruction length of each opcode, from unidasm), cases.txt (each opcode with 8 operand pairs plus 40,000
random instructions) and ee_cases.txt (the 2,048 opcode x operand cases)."""
import sys, os, subprocess, random
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
random.seed(7)
tmp = os.environ.get('TMPDIR', '/tmp')
L = {}
for op in range(256):
    p = '%s/oplen_%d.bin' % (tmp, os.getpid())
    open(p, 'wb').write(bytes([op, 0x12, 0x34]))
    line = subprocess.run(['unidasm', '-arch', 'm6510', p], capture_output=True, text=True).stdout.strip().splitlines()[0]
    toks = line.split()[1:]
    i = 0
    while i < len(toks) and len(toks[i]) == 2 and all(c in '0123456789abcdef' for c in toks[i]): i += 1
    L[op] = i
    os.unlink(p)
open(out + '/oplen.txt', 'w').write(''.join('%d %d\n' % kv for kv in L.items()))
pairs = [(0, 0), (0xff, 0xff), (0x34, 0x12), (0x80, 0x7f), (0xa5, 0x5a), (0x01, 0x00), (0xfe, 0x01), (0x7f, 0x80)]
base = [bytes([op, a, b][:L[op]]).hex() for op in range(256) for a, b in pairs]
open(out + '/ee_cases.txt', 'w').write('\n'.join(base) + '\n')
rnd = []
for i in range(40000):
    op = random.randrange(256)
    rnd.append(bytes([op, random.randrange(256), random.randrange(256)][:L[op]]).hex())
open(out + '/cases.txt', 'w').write('\n'.join(base + rnd) + '\n')
