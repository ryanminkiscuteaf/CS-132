import syntaxtree.*;
import visitor.*;
import xxx.*;

public class J2V {

	public static void main(String [] args) {
		Node root = null;
		try {
			root = new MiniJavaParser(System.in).Goal();
			//System.out.println("Program parsed successfully");
		}
		catch (ParseException e) {
			System.out.println(e.toString());
			System.exit(1);
		}

		// Build symbol table
		SymbolTable st = new SymbolTable();
		if (root.accept(st) == null) {
			System.out.println("Type error");
			System.exit(1);
		} else {
			// Type-check
			/*if (root.accept(new XTypeCheck(st)) == null) {
				System.out.println("Type error");
				System.exit(1);
			} else {
				//System.out.println("Program type checked successfully");
			}*/

			// Translate MiniJava to Vapor
			Translator t = new Translator(st);
			root.accept(t);
		}
	}

}