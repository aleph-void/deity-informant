"""run_sim65.py <dir> <cpu> : runs every NNNNN.sim with sim65, writes results.sim.txt: "<id> <hex of $0000-$0505>" """
import sys, os, subprocess, glob
from multiprocessing import Pool
SIM = os.path.join(os.environ.get('CC65_BIN', ''), 'sim65') if os.environ.get('CC65_BIN') else 'sim65'
d, cpu = sys.argv[1], sys.argv[2]
def one(p):
    r = subprocess.run([SIM, '--cpu', cpu, '-x', '100000', p], capture_output=True, timeout=20)
    return os.path.basename(p)[:5], r.stdout.hex(), r.returncode
if __name__ == '__main__':
    files = sorted(glob.glob(d + '/*.sim'))
    with Pool(8) as p: res = p.map(one, files, chunksize=32)
    with open(d + '/results.sim.txt', 'w') as f:
        for i, h, rc in res: f.write('%s %s\n' % (i, h))
