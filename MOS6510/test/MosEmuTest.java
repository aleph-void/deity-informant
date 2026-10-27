// P-code semantics check for the MOS 6510 module: hand-computed cases (test/emu/cases.txt) run in Ghidra's
// emulator.  Case format ('#' starts a comment):
//   case <name> [broken]        a "broken" case carries a deliberately wrong expectation and must FAIL
//   ctx <field>=<value>         context value for disassembly/emulation (smc_imm, smc_addr, smc_ctrl, smc_var)
//   code <hex bytes>            placed at $1000
//   asm <text> ; <text> ...     what the code must disassemble to, instruction by instruction
//   at <hex addr> <hex bytes>   extra bytes (data, pointers, subroutines) placed at that address
//   until <hex addr>            stop when PC reaches the address (default: end of code)
//   set <A|X|Y|S|N|V|Z|C|D|I|B>  <value>   register or flag; set mem:<hex addr> <value> sets a byte
//   expect <reg|flag|mem:<hex>|PC|SP> <value>
//   end
// Script args: <cases.txt> <outfile>
//@category Testing

import java.io.*;
import java.math.BigInteger;
import java.util.*;

import ghidra.app.emulator.EmulatorHelper;
import ghidra.app.script.GhidraScript;
import ghidra.app.util.PseudoInstruction;
import ghidra.program.model.address.*;
import ghidra.program.model.lang.*;
import ghidra.program.model.mem.*;

public class MosEmuTest extends GhidraScript {
	static class Case {
		String name;
		boolean broken;
		byte[] code;
		String[] asm;
		long until = -1;
		List<String[]> at = new ArrayList<>();
		Map<String, Long> ctx = new LinkedHashMap<>();
		Map<String, Long> set = new LinkedHashMap<>();
		Map<String, Long> expect = new LinkedHashMap<>();
	}

	static byte[] unhex(String s) {
		s = s.replace(" ", "");
		byte[] b = new byte[s.length() / 2];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
		}
		return b;
	}

	List<Case> load(String path) throws Exception {
		List<Case> cases = new ArrayList<>();
		Case c = null;
		try (BufferedReader r = new BufferedReader(new FileReader(path))) {
			String line;
			while ((line = r.readLine()) != null) {
				int h = line.indexOf('#');
				if (h >= 0 && !line.startsWith("asm ")) {
					line = line.substring(0, h);
				}
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				String[] f = line.split("\\s+");
				switch (f[0]) {
					case "case":
						c = new Case();
						c.name = f[1];
						c.broken = f.length > 2 && f[2].equals("broken");
						break;
					case "ctx":
						String[] kv = f[1].split("=");
						c.ctx.put(kv[0], Long.decode(kv[1]));
						break;
					case "code":
						c.code = unhex(line.substring(4));
						break;
					case "asm":
						c.asm = line.substring(3).trim().split("\\s*;\\s*");
						break;
					case "at":
						c.at.add(new String[] { f[1], line.substring(line.indexOf(f[1]) + f[1].length()) });
						break;
					case "until":
						c.until = Long.parseLong(f[1], 16);
						break;
					case "set":
						c.set.put(f[1], Long.decode(f[2]));
						break;
					case "expect":
						c.expect.put(f[1], Long.decode(f[2]));
						break;
					case "end":
						cases.add(c);
						break;
					default:
						throw new IllegalArgumentException("bad line: " + line);
				}
			}
		}
		return cases;
	}

	static String norm(String s) {
		return s.replace(" ", "").toLowerCase().replaceAll("0x0+([0-9a-f])", "0x$1");
	}

	String disassemble(Case c) {
		Language lang = currentProgram.getLanguage();
		Address base = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(0x1000);
		byte[] b = Arrays.copyOf(c.code, c.code.length + 8);
		StringBuilder sb = new StringBuilder();
		int off = 0;
		while (off < c.code.length) {
			try {
				ProcessorContextImpl ctx = new ProcessorContextImpl(lang);
				for (Map.Entry<String, Long> e : c.ctx.entrySet()) {
					ctx.setValue(lang.getRegister(e.getKey()), BigInteger.valueOf(e.getValue()));
				}
				MemBuffer buf = new ByteMemBufferImpl(base.add(off), Arrays.copyOfRange(b, off, b.length), true);
				InstructionPrototype p = lang.parse(buf, ctx, false);
				PseudoInstruction pi = new PseudoInstruction(currentProgram, base.add(off), p, buf, ctx);
				if (sb.length() > 0) {
					sb.append(" ; ");
				}
				sb.append(pi.toString());
				off += pi.getLength();
			}
			catch (Exception e) {
				sb.append(" ; BAD");
				break;
			}
		}
		return sb.toString();
	}

	String runCase(Case c) throws Exception {
		AddressSpace ram = currentProgram.getAddressFactory().getDefaultAddressSpace();
		if (c.asm != null) {
			String got = norm(disassemble(c));
			String want = norm(String.join(";", c.asm));
			if (!got.equals(want)) {
				return "disassembles as '" + disassemble(c) + "', not '" + String.join(" ; ", c.asm) + "'";
			}
		}
		EmulatorHelper emu = new EmulatorHelper(currentProgram);
		try {
			emu.writeMemory(ram.getAddress(0x1000), c.code);
			for (String[] a : c.at) {
				emu.writeMemory(ram.getAddress(Long.parseLong(a[0], 16)), unhex(a[1]));
			}
			emu.writeRegister("SH", 1);
			emu.writeRegister("S", 0xff);
			for (Map.Entry<String, Long> e : c.ctx.entrySet()) {
				emu.writeRegister(e.getKey(), e.getValue());
			}
			for (Map.Entry<String, Long> e : c.set.entrySet()) {
				if (e.getKey().startsWith("mem:")) {
					emu.writeMemory(ram.getAddress(Long.parseLong(e.getKey().substring(4), 16)),
						new byte[] { (byte) (long) e.getValue() });
				}
				else {
					emu.writeRegister(e.getKey(), e.getValue());
				}
			}
			emu.writeRegister(emu.getPCRegister(), 0x1000);
			long end = c.until >= 0 ? c.until : 0x1000 + c.code.length;
			int steps = 0;
			while (emu.getExecutionAddress().getOffset() != end) {
				if (++steps > 20000) {
					return "did not reach the end (pc " + emu.getExecutionAddress() + ")";
				}
				if (!emu.step(monitor)) {
					return "emulation error at " + emu.getExecutionAddress() + ": " + emu.getLastError();
				}
			}
			StringBuilder bad = new StringBuilder();
			for (Map.Entry<String, Long> e : c.expect.entrySet()) {
				String k = e.getKey();
				long got;
				if (k.startsWith("mem:")) {
					got = emu.readMemory(ram.getAddress(Long.parseLong(k.substring(4), 16)), 1)[0] & 0xff;
				}
				else if (k.equals("PC")) {
					got = emu.getExecutionAddress().getOffset();
				}
				else {
					got = emu.readRegister(k).longValue();
				}
				if (got != e.getValue()) {
					bad.append(String.format(" %s=0x%x (expected 0x%x)", k, got, e.getValue()));
				}
			}
			return bad.length() == 0 ? null : bad.toString().trim();
		}
		finally {
			emu.dispose();
		}
	}

	@Override
	protected void run() throws Exception {
		String[] args = getScriptArgs();
		List<Case> cases = load(args[0]);
		PrintStream out = new PrintStream(args[1]);
		int pass = 0, fail = 0, seen = 0, missed = 0;
		for (Case c : cases) {
			String err;
			try {
				err = runCase(c);
			}
			catch (Exception e) {
				err = "exception " + e;
			}
			if (c.broken) {
				if (err != null) {
					seen++;
					out.println("PASS (broken expectation detected) " + c.name + ": " + err);
				}
				else {
					missed++;
					out.println("FAIL " + c.name + ": the deliberately wrong expectation passed");
				}
			}
			else if (err == null) {
				pass++;
				out.println("PASS " + c.name);
			}
			else {
				fail++;
				out.println("FAIL " + c.name + ": " + err);
			}
		}
		out.printf("emulation: %d passed, %d failed, broken expectation %s%n", pass, fail + missed,
			seen > 0 && missed == 0 ? "detected" : "NOT detected");
		out.close();
	}
}
