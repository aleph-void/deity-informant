// Control-flow constructors of the self-modifying-code overlay (smc_ctrl): JMP, JSR and the eight branches, taken
// and not taken, executed in the PcodeEmulator with the context bit set, against hand-computed results.
// Script args: <outfile>
//@category Testing
import java.io.PrintStream;
import java.math.BigInteger;
import ghidra.app.script.GhidraScript;
import ghidra.pcode.emu.PcodeEmulator;
import ghidra.pcode.emu.PcodeThread;
import ghidra.program.model.address.*;
import ghidra.program.model.lang.*;

public class SmcCtrl extends GhidraScript {
	@Override
	protected void run() throws Exception {
		Language lang = currentProgram.getLanguage();
		AddressSpace ram = lang.getAddressFactory().getDefaultAddressSpace();
		PrintStream out = new PrintStream(getScriptArgs()[0]);
		int pass = 0, fail = 0;
		// opcode, flag to set (null for none), flag value, taken?
		Object[][] br = { { 0x10, "N", 0, true }, { 0x10, "N", 1, false }, { 0x30, "N", 1, true },
			{ 0x30, "N", 0, false }, { 0x50, "V", 0, true }, { 0x50, "V", 1, false }, { 0x70, "V", 1, true },
			{ 0x70, "V", 0, false }, { 0x90, "C", 0, true }, { 0x90, "C", 1, false }, { 0xB0, "C", 1, true },
			{ 0xB0, "C", 0, false }, { 0xD0, "Z", 0, true }, { 0xD0, "Z", 1, false }, { 0xF0, "Z", 1, true },
			{ 0xF0, "Z", 0, false } };
		java.util.List<Object[]> cases = new java.util.ArrayList<>();
		for (Object[] b : br) {
			cases.add(new Object[] { String.format("branch %02x %s=%d", b[0], b[1], b[2]),
				new byte[] { (byte) (int) b[0], 0x04 }, b[1], b[2], (boolean) b[3] ? 0x1006 : 0x1002 });
			// negative offset: $FC = -4 -> $1002 - 4 = $0FFE
			cases.add(new Object[] { String.format("branch %02x %s=%d back", b[0], b[1], b[2]),
				new byte[] { (byte) (int) b[0], (byte) 0xFC }, b[1], b[2], (boolean) b[3] ? 0x0FFE : 0x1002 });
		}
		cases.add(new Object[] { "jmp $2345", new byte[] { 0x4C, 0x45, 0x23 }, "Z", 0, 0x2345 });
		cases.add(new Object[] { "jsr $2345", new byte[] { 0x20, 0x45, 0x23 }, "Z", 0, 0x2345 });
		for (Object[] c : cases) {
			PcodeEmulator emu = new PcodeEmulator(lang);
			byte[] code = (byte[]) c[1];
			emu.getSharedState().setVar(ram, 0x1000, code.length, true, code);
			PcodeThread<byte[]> th = emu.newThread();
			th.overrideContextWithDefault();
			RegisterValue rv = new RegisterValue(lang.getContextBaseRegister());
			Register fr = lang.getRegister("smc_ctrl");
			rv = rv.assign(fr, new RegisterValue(fr, BigInteger.ONE));
			th.overrideContext(rv);
			th.getState().setVar(lang.getRegister("SH"), new byte[] { 1 });
			th.getState().setVar(lang.getRegister("S"), new byte[] { (byte) 0xFF });
			th.getState().setVar(lang.getRegister((String) c[2]), new byte[] { (byte) (int) c[3] });
			th.overrideCounter(ram.getAddress(0x1000));
			th.stepInstruction();
			long pc = th.getCounter().getOffset();
			boolean ok = pc == (int) c[4];
			String extra = "";
			if (((String) c[0]).startsWith("jsr")) {
				byte[] hi = emu.getSharedState().getVar(ram, 0x1FF, 1, true,
					ghidra.pcode.exec.PcodeExecutorStatePiece.Reason.INSPECT);
				byte[] lo = emu.getSharedState().getVar(ram, 0x1FE, 1, true,
					ghidra.pcode.exec.PcodeExecutorStatePiece.Reason.INSPECT);
				byte[] s = th.getState().getVar(lang.getRegister("S"),
					ghidra.pcode.exec.PcodeExecutorStatePiece.Reason.INSPECT);
				ok &= hi[0] == 0x10 && lo[0] == 0x02 && s[0] == (byte) 0xFD;
				extra = String.format(" stack %02x%02x S=%02x", hi[0], lo[0], s[0]);
			}
			out.printf("%s %s -> pc %04x%s%n", ok ? "PASS" : "FAIL", c[0], pc, extra);
			if (ok) {
				pass++;
			}
			else {
				fail++;
			}
		}
		out.printf("smc_ctrl: %d passed, %d failed%n", pass, fail);
		out.close();
	}
}
