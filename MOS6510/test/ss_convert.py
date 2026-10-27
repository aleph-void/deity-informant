"""ss_convert.py <dir with NN.json prefixes> <tests per opcode> <out.txt>
Converts SingleStepTests/65x02 6502/v1 JSON (one test per line) into the line format read by SsRun.java:
  <name>|<hex op>|pc s a x y p|addr:val,addr:val|pc s a x y p|addr:val,..."""
import sys, json, os
d, n, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
def regs(s): return '%d %d %d %d %d %d' % (s['pc'], s['s'], s['a'], s['x'], s['y'], s['p'])
def ram(s): return ','.join('%d:%d' % (a, v) for a, v in s['ram'])
cnt = 0
with open(out, 'w') as f:
    for op in range(256):
        c = 0
        for line in open('%s/%02x.json' % (d, op)):
            line = line.strip().rstrip(',')
            if not line.startswith('{'): continue
            try: t = json.loads(line)
            except ValueError: continue   # truncated last line
            f.write('%s|%02x|%s|%s|%s|%s\n' % (t['name'].replace(' ', '_'), op, regs(t['initial']), ram(t['initial']), regs(t['final']), ram(t['final'])))
            c += 1; cnt += 1
            if c >= n: break
print(cnt, 'tests')
