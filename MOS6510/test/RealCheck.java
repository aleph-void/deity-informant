// After import + auto-analysis: counts instructions, functions, bad-instruction and error bookmarks, decompiles
// every function, and writes every instruction as "<hex address> <hex bytes> <text>" for comparison with the
// reference disassemblers.  Script args: <summary.txt> <instructions.txt> [decompile-timeout-seconds]
//@category Testing
import java.io.*;
import ghidra.app.decompiler.*;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.block.*;

public class RealCheck extends GhidraScript {
	@Override
	protected void run() throws Exception {
		String[] a = getScriptArgs();
		int timeout = a.length > 2 ? Integer.parseInt(a[2]) : 30;
		Listing l = currentProgram.getListing();
		PrintStream ins = new PrintStream(a[1]);
		long n = 0;
		for (Instruction i : l.getInstructions(true)) {
			StringBuilder sb = new StringBuilder();
			for (byte b : i.getBytes()) {
				sb.append(String.format("%02x", b & 0xff));
			}
			ins.printf("%04x %s %s%n", i.getAddress().getOffset(), sb, i.toString());
			n++;
		}
		ins.close();
		int bad = 0, err = 0, badUninit = 0, badConflict = 0;
		var it = currentProgram.getBookmarkManager().getBookmarksIterator();
		while (it.hasNext()) {
			var bm = it.next();
			if (bm.getType().getTypeString().equals("Error")) {
				if (bm.getCategory().contains("Bad Instruction")) {
					bad++;
					if (bm.getComment().contains("uninitialized")) {
						badUninit++;
					}
					else if (bm.getComment().contains("conflicting instruction")) {
						badConflict++;
					}
				}
				else {
					err++;
				}
			}
		}
		DecompInterface di = new DecompInterface();
		di.openProgram(currentProgram);
		int funcs = 0, fail = 0, lines = 0;
		StringBuilder fails = new StringBuilder();
		for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
			funcs++;
			DecompileResults r = di.decompileFunction(f, timeout, monitor);
			if (!r.decompileCompleted()) {
				fail++;
				fails.append(" ").append(f.getName()).append(":").append(r.getErrorMessage());
			}
			else {
				lines += r.getDecompiledFunction().getC().split("\n").length;
			}
		}
		PrintStream out = new PrintStream(a[0]);
		out.printf("instructions %d, functions %d, bad-instruction bookmarks %d (%d flows into ROM not in the file, %d overlapping-instruction idioms, %d other), other error bookmarks %d, decompile failures %d/%d, decompiled lines %d%n",
			n, funcs, bad, badUninit, badConflict, bad - badUninit - badConflict, err, fail, funcs, lines);
		if (fail > 0) {
			out.println("failures:" + fails);
		}
		out.close();
	}
}
