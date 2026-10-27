//@category Testing
import ghidra.app.script.GhidraScript;
public class Bookmarks extends GhidraScript {
	@Override
	protected void run() throws Exception {
		var it = currentProgram.getBookmarkManager().getBookmarksIterator();
		while (it.hasNext()) {
			var b = it.next();
			if (b.getType().getTypeString().equals("Error")) {
				println("BM " + b.getAddress() + " " + b.getCategory() + " " + b.getComment());
			}
		}
	}
}
