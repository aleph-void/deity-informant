// Pre-analysis script: adds entry points. Args: each either <hex address> or ptr:<hex address> (a little-endian
// 16-bit pointer read from memory; the target becomes the entry point).
//@category Testing
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.symbol.*;
public class Seed extends GhidraScript {
	@Override
	protected void run() throws Exception {
		AddressSpace sp = currentProgram.getAddressFactory().getDefaultAddressSpace();
		for (String a : getScriptArgs()) {
			long v;
			if (a.startsWith("ptr:")) {
				Address p = sp.getAddress(Long.parseLong(a.substring(4), 16));
				v = (currentProgram.getMemory().getByte(p) & 0xff) | ((currentProgram.getMemory().getByte(p.add(1)) & 0xff) << 8);
			}
			else {
				v = Long.parseLong(a, 16);
			}
			Address t = sp.getAddress(v);
			currentProgram.getSymbolTable().addExternalEntryPoint(t);
			println("seed " + t);
		}
	}
}
