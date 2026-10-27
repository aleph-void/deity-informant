"""gen_prog.py <program.bin (linked for $1000)> <outdir> : one whole-program test in the gen_sem.py format."""
import sys, os
binf, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
code = open(binf, 'rb').read()
mem = bytearray(0x700)
mem[0xfc], mem[0xfd] = 0x00, 0x06
mem[0x602] = 1
CODE = 0x1000
end = CODE + len(code)
img = bytearray(0x2000)
img[:0x700] = mem
img[CODE:end] = code
tail = bytes([0xa9, 0x06, 0xa2, 0x05, 0x20, 0xf7, 0xff, 0xa9, 0x00, 0x20, 0xf9, 0xff])
img[end:end + len(tail)] = tail
hdr = b'sim65' + bytes([2, 0, 0xfc]) + (0).to_bytes(2, 'little') + CODE.to_bytes(2, 'little')
open('%s/00000.sim' % out, 'wb').write(hdr + bytes(img))
open('%s/tests.txt' % out, 'w').write('00000 00 prog 0 %x %s %s\n' % (end, code.hex(), bytes(mem).hex()))
