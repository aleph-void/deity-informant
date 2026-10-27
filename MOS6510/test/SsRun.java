// Runs SingleStepTests/65x02 (MIT) single-instruction tests in the PcodeEmulator: initial registers and RAM are loaded,
// one instruction is stepped, and the final registers (P compared without bits 4 and 5) and RAM are checked.
// Script args: <tests.txt> <out.txt>.  Output: one line per failure, then a summary per opcode.
//@category Testing
import java.io.*;
import java.util.*;
import ghidra.app.script.GhidraScript;
import ghidra.pcode.emu.PcodeEmulator;
import ghidra.pcode.emu.PcodeThread;
import ghidra.pcode.exec.PcodeExecutorStatePiece;
import ghidra.program.model.address.*;
import ghidra.program.model.lang.*;

public class SsRun extends GhidraScript {
	static final String[] FLAGS = { "C", "Z", "I", "D", "B", "", "V", "N" };

	@Override
	protected void run() throws Exception {
		Language lang = currentProgram.getLanguage();
		AddressSpace ram = lang.getAddressFactory().getDefaultAddressSpace();
		PrintStream out = new PrintStream(getScriptArgs()[1]);
		Map<String, int[]> perOp = new TreeMap<>();
		Map<String, String> firstFail = new TreeMap<>();
		Register[] flagRegs = new Register[8];
		for (int i = 0; i < 8; i++) {
			if (!FLAGS[i].isEmpty()) {
				flagRegs[i] = lang.getRegister(FLAGS[i]);
			}
		}
		Register rA = lang.getRegister("A"), rX = lang.getRegister("X"), rY = lang.getRegister("Y"),
			rS = lang.getRegister("S"), rSH = lang.getRegister("SH");
		try (BufferedReader r = new BufferedReader(new FileReader(getScriptArgs()[0]))) {
			String line;
			while ((line = r.readLine()) != null) {
				String[] f = line.split("\\|", -1);
				String op = f[1];
				int[] cnt = perOp.computeIfAbsent(op, k -> new int[2]);
				String[] ir = f[2].split(" ");
				String[] fr = f[4].split(" ");
				PcodeEmulator emu = new PcodeEmulator(lang);
				for (String kv : f[3].split(",")) {
					if (!kv.isEmpty()) {
						String[] p = kv.split(":");
						emu.getSharedState().setVar(ram, Long.parseLong(p[0]), 1, true,
							new byte[] { (byte) Integer.parseInt(p[1]) });
					}
				}
				PcodeThread<byte[]> th = emu.newThread();
				th.overrideContextWithDefault();
				th.getState().setVar(rA, new byte[] { (byte) Integer.parseInt(ir[2]) });
				th.getState().setVar(rX, new byte[] { (byte) Integer.parseInt(ir[3]) });
				th.getState().setVar(rY, new byte[] { (byte) Integer.parseInt(ir[4]) });
				th.getState().setVar(rS, new byte[] { (byte) Integer.parseInt(ir[1]) });
				th.getState().setVar(rSH, new byte[] { 1 });
				int p0 = Integer.parseInt(ir[5]);
				for (int i = 0; i < 8; i++) {
					if (flagRegs[i] != null) {
						th.getState().setVar(flagRegs[i], new byte[] { (byte) ((p0 >> i) & 1) });
					}
				}
				th.overrideCounter(ram.getAddress(Long.parseLong(ir[0])));
				String err = null;
				try {
					th.stepInstruction();
				}
				catch (Exception e) {
					err = "exception " + e;
				}
				if (err == null) {
					StringBuilder bad = new StringBuilder();
					long pc = th.getCounter().getOffset();
					if (pc != Long.parseLong(fr[0])) {
						bad.append(String.format(" pc=%04x(want %04x)", pc, Long.parseLong(fr[0])));
					}
					Object[][] chk = { { "S", rS, 1 }, { "A", rA, 2 }, { "X", rX, 3 }, { "Y", rY, 4 } };
					for (Object[] c : chk) {
						int got = th.getState().getVar((Register) c[1], PcodeExecutorStatePiece.Reason.INSPECT)[0] & 0xff;
						int want = Integer.parseInt(fr[(int) c[2]]);
						if (got != want) {
							bad.append(String.format(" %s=%02x(want %02x)", c[0], got, want));
						}
					}
					int p = 0x20;
					for (int i = 0; i < 8; i++) {
						if (flagRegs[i] != null) {
							p |= (th.getState().getVar(flagRegs[i], PcodeExecutorStatePiece.Reason.INSPECT)[0] & 1) << i;
						}
					}
					int wantP = Integer.parseInt(fr[5]);
					if ((p & 0xcf) != (wantP & 0xcf)) {
						bad.append(String.format(" P=%02x(want %02x)", p, wantP));
					}
					for (String kv : f[5].split(",")) {
						if (!kv.isEmpty()) {
							String[] q = kv.split(":");
							int got = emu.getSharedState().getVar(ram, Long.parseLong(q[0]), 1, true,
								PcodeExecutorStatePiece.Reason.INSPECT)[0] & 0xff;
							if (got != Integer.parseInt(q[1])) {
								bad.append(String.format(" mem[%s]=%02x(want %02x)", q[0], got, Integer.parseInt(q[1])));
							}
						}
					}
					if (bad.length() > 0) {
						err = bad.toString();
					}
				}
				if (err == null) {
					cnt[0]++;
				}
				else {
					cnt[1]++;
					firstFail.putIfAbsent(op, f[0] + ":" + err);
				}
			}
		}
		int tp = 0, tf = 0;
		for (Map.Entry<String, int[]> e : perOp.entrySet()) {
			tp += e.getValue()[0];
			tf += e.getValue()[1];
			out.printf("OP %s pass %d fail %d%s%n", e.getKey(), e.getValue()[0], e.getValue()[1],
				e.getValue()[1] > 0 ? "  first: " + firstFail.get(e.getKey()) : "");
		}
		out.printf("TOTAL pass %d fail %d%n", tp, tf);
		out.close();
	}
}
