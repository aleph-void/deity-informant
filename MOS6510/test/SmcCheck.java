// Checks the self-modifying-code overlay: with the context bit set, the instruction's p-code reads its operand
// from the code bytes (a LOAD from instruction address + 1) instead of using the decoded operand; without the bit
// the p-code never loads from there.  Script args: <outfile>
//@category Testing
import java.io.PrintStream;
import java.math.BigInteger;
import ghidra.app.script.GhidraScript;
import ghidra.app.util.PseudoInstruction;
import ghidra.program.model.address.Address;
import ghidra.program.model.lang.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.pcode.*;

public class SmcCheck extends GhidraScript {
	// hex bytes, context bit, expected mnemonic
	static final String[][] CASES = {
		{ "a912", "smc_imm", "LDA" }, { "6934", "smc_imm", "ADC" }, { "c912", "smc_imm", "CMP" },
		{ "a512", "smc_addr", "LDA" }, { "ad3412", "smc_addr", "LDA" }, { "bd3412", "smc_addr", "LDA" },
		{ "b912", "smc_addr", "LDA" }, { "a112", "smc_addr", "LDA" }, { "b112", "smc_addr", "LDA" },
		{ "8d3412", "smc_addr", "STA" }, { "e612", "smc_addr", "INC" }, { "a712", "smc_addr", "LAX" },
		{ "4c3412", "smc_ctrl", "JMP" }, { "203412", "smc_ctrl", "JSR" }, { "f010", "smc_ctrl", "BEQ" },
		{ "d010", "smc_ctrl", "BNE" }, { "9010", "smc_ctrl", "BCC" },
		{ "ad3412", "smc_var", "LDA" }, { "a912", "smc_var", "LDA" },
	};

	@Override
	protected void run() throws Exception {
		Language lang = currentProgram.getLanguage();
		Address a = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(0x1000);
		PrintStream out = new PrintStream(getScriptArgs()[0]);
		int ok = 0, bad = 0;
		for (String[] c : CASES) {
			for (int with = 0; with < 2; with++) {
				boolean smc = with == 1;
				String h = c[0];
				byte[] b = new byte[h.length() / 2 + 8];
				for (int i = 0; i < h.length() / 2; i++) {
					b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
				}
				ProcessorContextImpl ctx = new ProcessorContextImpl(lang);
				ctx.setRegisterValue(currentProgram.getProgramContext().getDefaultDisassemblyContext());
				if (smc) {
					ctx.setValue(lang.getRegister(c[1]), BigInteger.ONE);
				}
				MemBuffer buf = new ByteMemBufferImpl(a, b, true);
				InstructionPrototype p = lang.parse(buf, ctx, false);
				PseudoInstruction ins = new PseudoInstruction(currentProgram, a, p, buf, ctx);
				boolean loadsOperand = false;
				java.util.Set<Varnode> operandAddr = new java.util.HashSet<>();
				for (PcodeOp op : ins.getPcode()) {
					if (op.getOpcode() == PcodeOp.INT_ADD && op.getInput(0).isConstant() &&
						op.getInput(1).isConstant() &&
						op.getInput(0).getOffset() + op.getInput(1).getOffset() == 0x1001) {
						operandAddr.add(op.getOutput());
					}
					if (op.getOpcode() == PcodeOp.LOAD && (operandAddr.contains(op.getInput(1)) ||
						(op.getInput(1).isConstant() && op.getInput(1).getOffset() == 0x1001))) {
						loadsOperand = true;
					}
				}
				boolean good = ins.getMnemonicString().equals(c[2]) && (loadsOperand == smc ||
					(!smc && !loadsOperand));
				// without the context bit an immediate/zero-page constant must not be loaded from the code
				if (good) {
					ok++;
				}
				else {
					bad++;
				}
				out.printf("%s %s %s ctx=%s loads-operand-from-code=%s%n", good ? "PASS" : "FAIL", h, ins,
					smc ? c[1] : "-", loadsOperand);
			}
		}
		out.printf("smc overlay: %d passed, %d failed%n", ok, bad);
		out.close();
	}
}
