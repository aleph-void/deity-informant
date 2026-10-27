// Runs a 64 KiB image (Klaus Dormann's 6502_functional_test.bin, loaded at $0000, start $0400) in Ghidra's
// emulator until the program counter stops moving (a "jmp *" trap) or the step limit is reached.
// Script args: <image.bin> <success-pc-hex> <max-steps> <out.txt>
//@category Testing
import java.io.*;
import java.nio.file.*;
import ghidra.app.emulator.EmulatorHelper;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;

public class FuncTest extends GhidraScript {
	@Override
	protected void run() throws Exception {
		String[] a = getScriptArgs();
		byte[] img = Files.readAllBytes(Paths.get(a[0]));
		long success = Long.parseLong(a[1], 16);
		long max = Long.parseLong(a[2]);
		AddressSpace ram = currentProgram.getAddressFactory().getDefaultAddressSpace();
		EmulatorHelper emu = new EmulatorHelper(currentProgram);
		PrintStream out = new PrintStream(a[3]);
		long t0 = System.currentTimeMillis();
		emu.writeMemory(ram.getAddress(0), img);
		emu.writeRegister("SH", 1);
		emu.writeRegister(emu.getPCRegister(), 0x400);
		long steps = 0, prev = -1;
		String result = "step limit";
		while (steps < max) {
			long pc = emu.getExecutionAddress().getOffset();
			if (pc == prev) {
				result = (pc == success) ? "SUCCESS" : String.format("TRAP at %04x (test failed)", pc);
				break;
			}
			prev = pc;
			if (!emu.step(monitor)) {
				result = "emulation error at " + emu.getExecutionAddress() + ": " + emu.getLastError();
				break;
			}
			steps++;
		}
		long ms = System.currentTimeMillis() - t0;
		out.printf("%s after %d instructions (%d ms, %d instr/s)%n", result, steps, ms,
			steps * 1000 / Math.max(1, ms));
		out.close();
		emu.dispose();
	}
}
