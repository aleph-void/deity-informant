"""real_compare.py <name.ins> : compares Ghidra's disassembly of real code (RealCheck output: "addr bytes text") with
unidasm (MAME) and da65 (cc65) at the same addresses, instruction by instruction."""
import sys, os, subprocess, tempfile, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from oracle_common import canon, norm
from multiprocessing import Pool
DA65 = os.path.join(os.environ.get('CC65_BIN', ''), 'da65') if os.environ.get('CC65_BIN') else 'da65'
tmp = os.environ.get('TMPDIR', '/tmp')
def parse_u(line):
    toks = line.split(None)[1:]
    i = 0
    while i < len(toks) and len(toks[i]) == 2 and all(c in '0123456789abcdef' for c in toks[i]): i += 1
    return ' '.join(toks[i:])
def one(args):
    addr, hx, text = args
    fd, p = tempfile.mkstemp(dir=tmp, suffix='.bin'); os.write(fd, bytes.fromhex(hx)); os.close(fd)
    u = subprocess.run(['unidasm', '-arch', 'm6510', '-basepc', '0x%x' % int(addr, 16), p], capture_output=True, text=True).stdout.strip().splitlines()
    d = subprocess.run([DA65, '--cpu', '6502X', '-S', '0x%x' % int(addr, 16), '--comments', '0', p], capture_output=True, text=True).stdout.splitlines()
    os.unlink(p)
    u = parse_u(u[0]) if u else 'NONE'
    d = [l.strip() for l in d if l.startswith('        ') and not l.strip().startswith('.')]
    return addr, hx, text, u, (d[0] if d else 'NONE')
if __name__ == '__main__':
    rows = [l.rstrip('\n').split(' ', 2) for l in open(sys.argv[1])]
    with Pool(8) as p: res = p.map(one, rows, chunksize=32)
    c = collections.Counter(); ex = []
    for addr, hx, text, u, d in res:
        g = norm(canon(text)); gu = norm(canon(u)); gd = norm(canon(d))
        ku = g == gu; kd = g == gd or (gd is None and g is not None and g[2] is not None and g[1] == 'abs' and False)
        c[(ku, kd)] += 1
        if not (ku and kd) and len(ex) < 8: ex.append((addr, hx, text, u, d))
    n = len(res)
    print('%s: %d instructions; identical to unidasm %d, to da65 %d, to both %d' % (sys.argv[1], n, sum(v for k, v in c.items() if k[0]), sum(v for k, v in c.items() if k[1]), c[(True, True)]))
    for e in ex: print('   ', e)
