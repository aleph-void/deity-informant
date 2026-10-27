"""oracle_dump.py <cases.txt> <out_unidasm.txt> <out_da65.txt> : per-case black-box disassembly (hex bytes per line, base 0x1000)."""
import subprocess, sys, os, tempfile
from multiprocessing import Pool
DA65 = os.path.join(os.environ.get('CC65_BIN', ''), 'da65') if os.environ.get('CC65_BIN') else 'da65'
tmp = os.environ.get('TMPDIR', '/tmp')
def parse_u(line):
    toks = line.split(None)[1:]
    i = 0
    while i < len(toks) and len(toks[i]) == 2 and all(c in '0123456789abcdef' for c in toks[i]):
        i += 1
    return ' '.join(toks[i:])
def one(h):
    b = bytes.fromhex(h)
    fd, p = tempfile.mkstemp(dir=tmp, suffix='.bin'); os.write(fd, b); os.close(fd)
    u = subprocess.run(['unidasm', '-arch', 'm6510', '-basepc', '0x1000', p], capture_output=True, text=True).stdout.strip().splitlines()
    d = subprocess.run([DA65, '--cpu', '6502X', '-S', '0x1000', '--comments', '0', p], capture_output=True, text=True).stdout.splitlines()
    os.unlink(p)
    u = parse_u(u[0]) if u else 'NONE'
    # unidasm: "1000: a7 10     lax $10" -> drop address and bytes
    d = [l.strip() for l in d if l.startswith('        ') and not l.strip().startswith('.')]
    return h, u, (d[0] if d else 'NONE')
if __name__ == '__main__':
    cases = [l.strip() for l in open(sys.argv[1]) if l.strip()]
    with Pool(8) as p:
        res = p.map(one, cases, chunksize=64)
    with open(sys.argv[2], 'w') as fu, open(sys.argv[3], 'w') as fd:
        for h, u, d in res:
            fu.write('%s\t%s\n' % (h, u)); fd.write('%s\t%s\n' % (h, d))
