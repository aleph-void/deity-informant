"""gen_sem.py <outdir> [--per-op N] [--seed S] : single-instruction semantic tests for Ghidra's p-code vs cc65 sim65 (6502X).
Each test is a small program at $1000 (set state, one instruction, store state) plus initial memory $0000-$04FF.
Writes <outdir>/tests.txt (for the Ghidra script) and <outdir>/NNNN.sim (sim65 images)."""
import sys, os, random, re, subprocess, collections
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
PER = 24; seed = 1
a = sys.argv[2:]
for i, x in enumerate(a):
    if x == '--per-op': PER = int(a[i + 1])
    if x == '--seed': seed = int(a[i + 1])
rnd = random.Random(seed)
here = os.path.dirname(os.path.abspath(__file__))
# opcode -> addressing mode, from the black-box oracle (unidasm) on a probe encoding
def mode_of(op):
    open(os.environ.get('TMPDIR', '/tmp') + '/probe_%d.bin' % os.getpid(), 'wb').write(bytes([op, 0x12, 0x34]))
    t = subprocess.run(['unidasm', '-arch', 'm6510', os.environ.get('TMPDIR', '/tmp') + '/probe_%d.bin' % os.getpid()], capture_output=True, text=True).stdout.split('\n')[0]
    toks = t.split()[1:]
    i = 0
    while i < len(toks) and len(toks[i]) == 2 and all(c in '0123456789abcdef' for c in toks[i]): i += 1
    mn = toks[i]; o = ''.join(toks[i + 1:]); n = i
    if not o: return mn, 'impl', n
    if o == 'a': return mn, 'acc', n
    if o.startswith('#'): return mn, 'imm', n
    if re.fullmatch(r'\(.*,x\)', o): return mn, 'indx', n
    if re.fullmatch(r'\(.*\),y', o): return mn, 'indy', n
    if o.endswith(',x'): return mn, ('zpx' if n == 2 else 'absx'), n
    if o.endswith(',y'): return mn, ('zpy' if n == 2 else 'absy'), n
    return mn, ('zp' if n == 2 else 'abs'), n
SKIP = {0x00, 0x20, 0x40, 0x60, 0x4c, 0x6c, 0x10, 0x30, 0x50, 0x70, 0x90, 0xb0, 0xd0, 0xf0,
        0x02, 0x12, 0x22, 0x32, 0x42, 0x52, 0x62, 0x72, 0x92, 0xb2, 0xd2, 0xf2}
tests = []
def build(op, mode, n):
    mem = bytearray(0x700)
    for i in range(0x10, 0xf0): mem[i] = rnd.randrange(256)
    for i in range(0x200, 0x400): mem[i] = rnd.randrange(256)
    mem[0xfc], mem[0xfd] = 0x00, 0x06      # sim65's software stack pointer, read by its write hook
    mem[0x600], mem[0x601] = 0x00, 0x00; mem[0x602], mem[0x603] = 1, 0
    A, X, Y = rnd.randrange(256), rnd.randrange(256), rnd.randrange(256)
    if rnd.random() < .3: X = rnd.choice([0, 1, 0xff, 0x80, 0x7f, 0x10])
    if rnd.random() < .3: Y = rnd.choice([0, 1, 0xff, 0x80, 0x7f, 0x10])
    if rnd.random() < .3: A = rnd.choice([0, 1, 0xff, 0x80, 0x7f])
    P = rnd.randrange(256) & 0xcf | 0x20 & 0xff
    P &= ~0x04      # I irrelevant
    if rnd.random() < .3: P |= 0x08
    else: P &= ~0x08
    S0 = rnd.randrange(0x50, 0xf0)
    bs = bytes([op])
    zpeff = lambda: rnd.randrange(0x10, 0xf0)
    abseff = lambda: rnd.randrange(0x200, 0x400)
    wrap = False
    if mode == 'imm': bs += bytes([rnd.randrange(256)])
    elif mode == 'zp': bs += bytes([zpeff()])
    elif mode == 'zpx': e = zpeff(); bs += bytes([(e - X) & 255]) if X <= e or True else b''
    elif mode == 'zpy': e = zpeff(); bs += bytes([(e - Y) & 255])
    elif mode == 'abs': e = abseff(); bs += bytes([e & 255, e >> 8])
    elif mode == 'absx': e = abseff(); b = (e - X) & 0xffff; bs += bytes([b & 255, b >> 8])
    elif mode == 'absy': e = abseff(); b = (e - Y) & 0xffff; bs += bytes([b & 255, b >> 8])
    elif mode == 'indx':
        slot = 0xff if rnd.random() < .15 else rnd.randrange(0x10, 0xee)
        e = abseff(); bs += bytes([(slot - X) & 255])
        if slot == 0xff: mem[0xff] = e & 255; mem[0x00] = e >> 8; wrap = True
        else: mem[slot] = e & 255; mem[slot + 1] = e >> 8
    elif mode == 'indy':
        slot = 0xff if rnd.random() < .15 else rnd.randrange(0x10, 0xee)
        e = abseff(); b = (e - Y) & 0xffff; bs += bytes([slot])
        if slot == 0xff: mem[0xff] = b & 255; mem[0x00] = b >> 8; wrap = True
        else: mem[slot] = b & 255; mem[slot + 1] = b >> 8
    code = bytes([0xa2, S0, 0x9a, 0xa9, P, 0x48, 0xa9, A, 0xa2, X, 0xa0, Y, 0x28]) + bs
    # epilogue: store A X Y P S at $0500..
    code += bytes([0x8d, 0x00, 0x05, 0x8e, 0x01, 0x05, 0x8c, 0x02, 0x05, 0x08, 0x68, 0x8d, 0x03, 0x05, 0xba, 0x8e, 0x04, 0x05])
    return dict(op=op, mode=mode, code=code, mem=bytes(mem), wrap=wrap, regs=(A, X, Y, P, S0))
ops = [o for o in range(256) if o not in SKIP]
modes = {o: mode_of(o) for o in ops}
for o in ops:
    mn, mode, n = modes[o]
    for k in range(PER):
        tests.append(build(o, mode, n))
CODE = 0x1000
def sim_image(t):
    img = bytearray(0x2000)
    img[:0x700] = t['mem']
    img[CODE:CODE + len(t['code'])] = t['code']
    end = CODE + len(t['code'])
    # JSR $FFF7 (write): A/X = count 0x0506 ; then JSR $FFF9 (exit)
    tail = bytes([0xa9, 0x06, 0xa2, 0x05, 0x20, 0xf7, 0xff, 0xa9, 0x00, 0x20, 0xf9, 0xff])
    img[end:end + len(tail)] = tail
    # software stack: [0x600]=buf (0x0000), [0x602]=fd(1); count includes the register block at 0x500
    return bytes(img), end
with open(out + '/tests.txt', 'w') as f:
    for i, t in enumerate(tests):
        img, end = sim_image(t)
        hdr = b'sim65' + bytes([2, 1 if False else 0, 0xfc]) + (0).to_bytes(2, 'little') + CODE.to_bytes(2, 'little')
        open('%s/%05d.sim' % (out, i), 'wb').write(hdr + img)
        f.write('%05d %02x %s %d %x %s %s\n' % (i, t['op'], t['mode'], 1 if t['wrap'] else 0, end, t['code'].hex(), t['mem'].hex()))
print(len(tests), 'tests')
