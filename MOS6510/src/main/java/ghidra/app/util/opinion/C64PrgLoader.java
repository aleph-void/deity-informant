/* ###
 * IP: Ghidra-Rare MOS6510 module
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.util.opinion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ghidra.app.util.bin.ByteProvider;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSpace;
import ghidra.program.model.lang.LanguageCompilerSpecPair;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.symbol.SourceType;
import ghidra.util.exception.CancelledException;

/**
 * Loader for Commodore 64 {@code .prg} files: a two-byte little-endian load address followed by the
 * program bytes.  The bytes are placed at the load address.  A BASIC stub ({@code 10 SYS 2064}) at $0801
 * names the machine-code entry point, which becomes the {@code start} entry symbol.
 */
public class C64PrgLoader extends AbstractProgramWrapperLoader {

	public static final String NAME = "Commodore 64 PRG";
	private static final String LANGUAGE = "6510:LE:16:default";
	private static final int TOKEN_SYS = 0x9E;

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public Collection<LoadSpec> findSupportedLoadSpecs(ByteProvider provider) throws IOException {
		List<LoadSpec> specs = new ArrayList<>();
		String name = provider.getName();
		long length = provider.length();
		if (name == null || !name.toLowerCase().endsWith(".prg") || length < 3 || length > 2 + 0x10000) {
			return specs;
		}
		int load = loadAddress(provider);
		if (load + length - 2 > 0x10000) {
			return specs;
		}
		specs.add(new LoadSpec(this, load, new LanguageCompilerSpecPair(LANGUAGE, "default"), true));
		return specs;
	}

	private static int loadAddress(ByteProvider provider) throws IOException {
		return (provider.readByte(0) & 0xff) | ((provider.readByte(1) & 0xff) << 8);
	}

	/** Entry address named by a BASIC SYS line at the start of the program, or -1. */
	static int sysTarget(byte[] data, int load) {
		if (load != 0x0801 || data.length < 10) {
			return -1;
		}
		// next-line pointer (2), line number (2), then tokens: SYS (0x9E), optional spaces, decimal digits
		int i = 4;
		if ((data[i] & 0xff) != TOKEN_SYS) {
			return -1;
		}
		i++;
		while (i < data.length && data[i] == ' ') {
			i++;
		}
		int value = 0, digits = 0;
		while (i < data.length && data[i] >= '0' && data[i] <= '9' && digits < 6) {
			value = value * 10 + (data[i] - '0');
			digits++;
			i++;
		}
		return digits > 0 && value <= 0xffff ? value : -1;
	}

	/**
	 * Uninitialized placeholder for a region the PRG does not occupy: the ROMs (call targets such as
	 * CHROUT at $FFD2 then resolve) and the I/O window (volatile).  Which of them is visible depends on the
	 * 6510 port at $01 at run time; Ghidra's address map is static, so the usual configuration is shown.
	 */
	private void addRomRegion(Program program, AddressSpace space, String name, int first, int last,
			int load, long length, boolean volatileBlock) throws Exception {
		if (load <= last && load + length - 1 >= first) {
			return;
		}
		MemoryBlock block = program.getMemory().createUninitializedBlock(name, space.getAddress(first),
			last - first + 1, false);
		block.setRead(true);
		block.setWrite(volatileBlock);
		block.setExecute(true);
		block.setVolatile(volatileBlock);
	}

	/**
	 * The KERNAL jump table ($FF81-$FFF5, 13 bytes short of the vectors) as RTS stubs, so a JSR to CHROUT,
	 * GETIN... resolves to a function (named by the language's default symbols) instead of a bad instruction.
	 * The real ROM bodies are not part of a PRG and are not invented.
	 */
	private void addKernalJumpTable(Program program, AddressSpace space, int load, long length)
			throws Exception {
		int first = 0xFF81, last = 0xFFF5;
		if (load <= last && load + length - 1 >= first) {
			return;
		}
		byte[] stubs = new byte[last - first + 1];
		for (int i = 0; i < stubs.length; i += 3) {
			stubs[i] = 0x60; // RTS
		}
		MemoryBlock block = program.getMemory().createInitializedBlock("KERNAL_JUMPTABLE",
			space.getAddress(first), new java.io.ByteArrayInputStream(stubs), stubs.length,
			new ghidra.util.task.TaskMonitorAdapter(), false);
		block.setRead(true);
		block.setWrite(false);
		block.setExecute(true);
	}

	@Override
	protected void load(Program program, ImporterSettings settings)
			throws CancelledException, IOException {
		ByteProvider provider = settings.provider();
		int load = (int) settings.loadSpec().getDesiredImageBase();
		AddressSpace space = program.getAddressFactory().getDefaultAddressSpace();
		long length = provider.length() - 2;
		try {
			byte[] data = provider.readBytes(2, length);
			Address start = space.getAddress(load);
			try (InputStream in = provider.getInputStream(2)) {
				MemoryBlock block = program.getMemory().createInitializedBlock("PRG", start, in, length,
					settings.monitor(), false);
				block.setRead(true);
				block.setWrite(true);
				block.setExecute(true);
			}
			addRomRegion(program, space, "BASIC_ROM", 0xA000, 0xBFFF, load, length, false);
			addRomRegion(program, space, "IO", 0xD000, 0xDFFF, load, length, true);
			addRomRegion(program, space, "KERNAL_ROM", 0xE000, 0xFF80, load, length, false);
			addKernalJumpTable(program, space, load, length);
			addRomRegion(program, space, "KERNAL_VECTORS", 0xFFF6, 0xFFFF, load, length, false);
			int entry = sysTarget(data, load);
			if (entry < 0) {
				entry = load;
			}
			Address entryAddr = space.getAddress(entry);
			program.getSymbolTable().createLabel(entryAddr, "start", SourceType.IMPORTED);
			program.getSymbolTable().addExternalEntryPoint(entryAddr);
		}
		catch (CancelledException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IOException("cannot load PRG: " + e.getMessage(), e);
		}
	}
}
