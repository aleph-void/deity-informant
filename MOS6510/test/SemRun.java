// Same programs as SemRun, run in Ghidra's PcodeEmulator (which lets the context register be overridden, so the
// self-modifying-code overlay constructors can be executed).  Script args: <tests.txt> <out.txt> [ctx field ...]
//@category Testing
import java.io.*;
import java.math.BigInteger;
import ghidra.app.script.GhidraScript;
import ghidra.pcode.emu.PcodeEmulator;
import ghidra.pcode.emu.PcodeThread;
import ghidra.program.model.address.*;
import ghidra.program.model.lang.*;

public class SemRun extends GhidraScript {
	@Override
	protected void run() throws Exception {
		String[] args = getScriptArgs();
		Language lang = currentProgram.getLanguage();
		AddressSpace ram = lang.getAddressFactory().getDefaultAddressSpace();
		PrintStream out = new PrintStream(args[1]);
		try (BufferedReader r = new BufferedReader(new FileReader(args[0]))) {
			String line;
			while ((line = r.readLine()) != null) {
				String[] f = line.split(" ");
				byte[] code = unhex(f[5]);
				byte[] mem = unhex(f[6]);
				PcodeEmulator emu = new PcodeEmulator(lang);
				emu.getSharedState().setVar(ram, 0, mem.length, true, mem);
				emu.getSharedState().setVar(ram, 0x1000, code.length, true, code);
				byte[] tail = { (byte) 0xa9, 0x06, (byte) 0xa2, 0x05, 0x20, (byte) 0xf7, (byte) 0xff };
				emu.getSharedState().setVar(ram, 0x1000 + code.length, tail.length, true, tail);
				PcodeThread<byte[]> th = emu.newThread();
				th.overrideContextWithDefault();
				RegisterValue rv = new RegisterValue(lang.getContextBaseRegister());
				for (int i = 2; i < args.length; i++) {
					Register fr = lang.getRegister(args[i]);
					rv = rv.assign(fr, new RegisterValue(fr, BigInteger.ONE));
				}
				th.getState().setVar(lang.getRegister("SH"), new byte[] { 1 });
				th.overrideCounter(ram.getAddress(0x1000));
				String err = null;
				int steps = 0;
				while (th.getCounter().getOffset() != 0xFFF7) {
					if (++steps > 100000) {
						err = "ERR steps";
						break;
					}
					try {
						if (args.length > 2) {
							th.overrideContext(rv);   // noflow fields do not carry to the next instruction
						}
						th.stepInstruction();
					}
					catch (Exception e) {
						err = "ERR " + e;
						break;
					}
				}
				if (err != null) {
					out.println(f[0] + " " + err.replace('\n', ' '));
					continue;
				}
				byte[] res = emu.getSharedState().getVar(ram, 0, 0x506, true, ghidra.pcode.exec.PcodeExecutorStatePiece.Reason.INSPECT);
				StringBuilder sb = new StringBuilder();
				for (byte b : res) {
					sb.append(String.format("%02x", b & 0xff));
				}
				out.println(f[0] + " " + sb);
			}
		}
		out.close();
	}

	static byte[] unhex(String s) {
		byte[] b = new byte[s.length() / 2];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
		}
		return b;
	}
}
