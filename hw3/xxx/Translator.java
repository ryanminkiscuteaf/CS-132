package xxx;

import syntaxtree.*;
import visitor.DepthFirstVisitor;
import java.util.*;

public class Translator extends DepthFirstVisitor {

	private SymbolTable symbolTable = new SymbolTable();
	
	private LastExp lastExp = null;
	private int indent = 0;
	private boolean typeIdentifierScope = false;
	private boolean allocArray = false;
	
	private int varNameCount = 0;
	private int elseLabelCount = 0;
	private int ifEndLabelCount = 0;
	private int nullLabelCount = 0;
	private int whileLabelCount = 0;
	private int whileElseLabelCount = 0;
	private int andElseLabelCount = 0;
	private int andEndLabelCount = 0;
	private int notElseLabelCount = 0;
	private int notEndLabelCount = 0;
	private int outofboundsLabelCount = 0;

	public static class LE {
		public enum Type {INT, BOOLEAN, ID, EXP}
	}

	public Translator(SymbolTable st) {
		symbolTable = st;
	}

	public class LastExp {
		LE.Type type;
		String string;
		ClassType classType;

		public LastExp(String s, LE.Type t) {
			this(s, t, null);
		}

		public LastExp(String s, LE.Type t, ClassType ct) {
			string = s;
			type = t;
			classType = ct;
		}	
	}

	private void resetNamingCount() {
		varNameCount = 0;
	}

	private String varName() {
		return "t." + varNameCount++;
	}

	private String elseLabel() {
		return "if1_else_" + elseLabelCount++;
	}

	private String ifEndLabel() {
		return "if1_end_" + ifEndLabelCount++;
	}

	private String nullLabel() {
		return "null" + nullLabelCount++;
	}

	private String whileLabel() {
		return "while_" + whileLabelCount++;
	}

	private String whileElseLabel() {
		return "while_else_" + whileElseLabelCount++;
	}

	private String andElseLabel() {
		return "and_else_" + andElseLabelCount++;
	}

	private String andEndLabel() {
		return "and_end_" + andEndLabelCount++;
	}

	private String notElseLabel() {
		return "not_else_" + notElseLabelCount++;
	}

	private String notEndLabel() {
		return "not_end_" + notEndLabelCount++;
	}

	private String outofboundsLabel() {
		return "out_of_bounds_" + outofboundsLabelCount++;
	}

	private int getMethodIndex(ClassType ct, String method) {
		int index = 0;
		for (MethodType mt : ct.methods.values()) {
			if (mt.name.equals(method))
				return index;
			index++;
		}
		return -1; // Only if the methods are not found in its class
	}

	private int getFieldIndex(ClassType ct, String field) {
		int index = 0;
		for (String f : ct.vars.keySet()) {
			if (f.equals(field))
				return index;
			index++;
		}
		// At this point we know that the class field belongs
		// to our ancestor(s)
		ClassType parent = ct.parent;

		while (parent != null) {
			for (String f : parent.vars.keySet()) {
				if (f.equals(field))
					return index;
				index++;
			}
			parent = parent.parent;
		}
		// In practice, this will never happen bcos
		// we already type-check
		return -1; 
	}

	private int getNumFields(ClassType ct) {
		int numFields = ct.vars.values().size();

		ClassType parent = ct.parent;

		while (parent != null) {
			numFields += parent.vars.values().size();
			parent = parent.parent;
		}

		return numFields;
	}

	private void printx(String str, Object... args) {
		String indentStr = "";
		for (int i = 0; i < indent; i++)
			indentStr += "  ";

		System.out.println(String.format(indentStr + str, args));
	}

	private String printVar(LastExp le) {
		String v;
		if (le.type == LE.Type.EXP) {
			v = varName();
			printx("%s = %s", v, le.string);
		} else {
			v = le.string;
		}
		return v;
	}

	private void printNullPointer(String addr) {
		String nullLabel = nullLabel();
		printx("if %s goto :%s", addr, nullLabel);
		indent++;
		printx("Error(\"null pointer\")");
		indent--;
		printx("%s:", nullLabel);
	}	

	private void printArrayAllocFunc() {
		printx("func ArrayAllocZ(size)");
		indent++;
		printx("bytes = Add(size 1)");
		printx("bytes = MulS(bytes 4)");
		printx("addr = HeapAllocZ(bytes)");
		printx("if addr goto :arraynull");
		indent++;
		printx("Error(\"null pointer\")");
		indent--;
		printx("arraynull:");
		printx("[addr] = size");
		printx("ret addr");
		indent--;
	}

	private void printOutOfBounds(String baseAddr, String index) {
		String size = varName();
		String cond1 = varName();
		String cond2 = varName();
		String cond3 = varName();
		String outofboundsLabel = outofboundsLabel();

		printx("%s = [%s]", size, baseAddr);
		printx("%s = LtS(%s %s)", cond1, index, 0);
		printx("%s = Eq(%s %s)", cond2, index, size);
		printx("%s = LtS(%s %s)", cond3, size, index); // size < index === index > size
		printx("%s = Add(%s %s)", cond2, cond1, cond2);
		printx("%s = Add(%s %s)", cond3, cond2, cond3);
		printx("if0 %s goto :%s", cond3, outofboundsLabel);
		indent++;
		printx("Error(\"array index out of bounds\")");
		indent--;
		printx("%s:", outofboundsLabel);	
	}

	private String getParamsString(LinkedHashMap<String, XType> params) {
		StringBuilder paramsStr = new StringBuilder();

		for (String p : params.keySet()) {
			paramsStr.append(" " + p);
		}

		return paramsStr.toString();
	}

	/* === Visitors === */

	public void visit(Goal n) {
		n.f0.accept(this);
      	n.f1.accept(this);

      	// Print the helper function that allocates memory for array
      	if (allocArray) {
      		System.out.println();
      		printArrayAllocFunc();
      	}
	}

	public void visit(MainClass n) {
		// Print classes and their methods
		// so other functions can refer to them later
		for (ClassType ct : symbolTable.classes.values()) {
			// Skip the main class
			if ( !ct.name.equals(n.f1.f0.toString()) ) {
				printx("const vmt_%s", ct.name);
				indent++;

				// Print methods
				for (String methodName : ct.methods.keySet())
					printx(":%s.%s", ct.name, methodName);

				indent--;
				System.out.println();
			}
		}

		printx("func Main()");
		indent++;

		n.f15.accept(this);

		printx("ret");
		indent--;
	}

	public void visit(TypeDeclaration n) {
		n.f0.accept(this);
	}

	public void visit(ClassDeclaration n) {
		// Begin class scope
		symbolTable.scopeClass = symbolTable.classes.get(n.f1.f0.toString());

		// MethodDeclaration
		n.f4.accept(this);

		// End class scope
		symbolTable.scopeClass = null;
	}

	public void visit(ClassExtendsDeclaration n) {
		// Begin class scope
		symbolTable.scopeClass = symbolTable.classes.get(n.f1.f0.toString());

		// MethodDeclaration
		n.f6.accept(this);

		// End class scope
		symbolTable.scopeClass = null;
	}

	public void visit(MethodDeclaration n) {

		System.out.println();

		MethodType mt = symbolTable.scopeClass.methods.get(n.f2.f0.toString());

		// Begin method scope
		symbolTable.scopeMethod = mt;

		printx("func %s.%s(this%s)", symbolTable.scopeClass.name, mt.name, getParamsString(mt.params));
		resetNamingCount();
		indent++;

		// Statement*
		n.f8.accept(this);

		// Return Expression
		n.f10.accept(this);

		printx("ret %s", printVar(lastExp));
		indent--;

		// End method scope
		symbolTable.scopeMethod = null;
	}

	public void visit(Statement n) {
		n.f0.accept(this);
	}

	public void visit(Block n) {
		n.f1.accept(this);
	}

	public void visit(AssignmentStatement n) {
		// Identifier
		n.f0.accept(this);
		LastExp lhs = lastExp;

		// Expression
		n.f2.accept(this);
		LastExp rhs = lastExp;

		String val;
		if (lhs.type == LE.Type.EXP && rhs.type == LE.Type.EXP) {
			val = varName();
			printx("%s = %s", val, rhs.string);
		} else {
			val = rhs.string;
		}

		printx("%s = %s", lhs.string, val);
	}

	public void visit(ArrayAssignmentStatement n) {
		// Identifier
		n.f0.accept(this);
		String baseAddr = printVar(lastExp);

		printNullPointer(baseAddr);
	
		// Array Index Expression
		n.f2.accept(this);
		String index = printVar(lastExp);

		printOutOfBounds(baseAddr, index);
		
		String offset = varName();
		String bytes = varName();
		
		printx("%s = Add(%s 1)", offset, index);
		printx("%s = MulS(%s 4)", offset, offset);
		printx("%s = Add(%s %s)", bytes, baseAddr, offset);

		// Array Elem New Value
		n.f5.accept(this);

		printx("[%s] = %s", bytes, printVar(lastExp));
	}

	public void visit(IfStatement n) {
		// Expression
		n.f2.accept(this);

		String elseLabel = elseLabel();
		String ifEndLabel = ifEndLabel();
		String condVar = printVar(lastExp);

		printx("if0 %s goto :%s", condVar, elseLabel);
		indent++;

		// If Statement
		n.f4.accept(this);

		printx("goto :%s", ifEndLabel);
		indent--;
		printx("%s:", elseLabel);
		indent++;

		// Else Statement
		n.f6.accept(this);

		indent--;
		printx("%s:", ifEndLabel);
	}

	public void visit(WhileStatement n) {
		String whileLabel = whileLabel();
		String elseLabel = whileElseLabel();

		// IMPORTANT: make sure the while label
		// goes to the point where conditional
		// expression is updated and checked
		printx("%s:", whileLabel);

		// While Expression
		n.f2.accept(this);

		printx("if0 %s goto :%s", printVar(lastExp), elseLabel);
		indent++;

		// Body Statement
		n.f4.accept(this);

		printx("goto :%s", whileLabel);
		indent--;
		printx("%s:", elseLabel);
	}

	public void visit(PrintStatement n) {
		// Expression
		n.f2.accept(this);

		printx("PrintIntS(%s)", printVar(lastExp));
	}

	public void visit(Expression n) {
		n.f0.accept(this);
	}

	public void visit(AndExpression n) {
		// PrimaryExpression
		n.f0.accept(this);
		LastExp lhs = lastExp;

		String andElseLabel = andElseLabel();
		String andEndLabel = andEndLabel();
		String vl = printVar(lhs);

		printx("if0 %s goto :%s", vl, andElseLabel);
		indent++;

		// PrimaryExpression
		n.f2.accept(this);
		LastExp rhs = lastExp;

		String vr = printVar(rhs);
		String finalRes = varName();

		printx("%s = %s", finalRes, vr);
		printx("goto :%s", andEndLabel);
		indent--;
		printx("%s:", andElseLabel);
		printx("%s = 0", finalRes);
		printx("%s:", andEndLabel);
		
		lastExp = new LastExp(finalRes, LE.Type.ID);
	}

	public void visit(CompareExpression n) {
		// PrimaryExpression
		n.f0.accept(this);
		LastExp lhs = lastExp;
		
		// PrimaryExpression
		n.f2.accept(this);
		LastExp rhs = lastExp;
		
		lastExp = new LastExp(String.format("LtS(%s %s)", printVar(lhs), printVar(rhs)), LE.Type.EXP);
	}

	public void visit(PlusExpression n) {
		// PrimaryExpression
		n.f0.accept(this);
		LastExp lhs = lastExp;

		// PrimaryExpression
		n.f2.accept(this);
		LastExp rhs = lastExp;

		lastExp = new LastExp(String.format("Add(%s %s)", printVar(lhs), printVar(rhs)), LE.Type.EXP);
	}

	public void visit(MinusExpression n) {
		// PrimaryExpression
		n.f0.accept(this);
		LastExp lhs = lastExp;

		// PrimaryExpression
		n.f2.accept(this);
		LastExp rhs = lastExp;

		lastExp = new LastExp(String.format("Sub(%s %s)", printVar(lhs), printVar(rhs)), LE.Type.EXP);
	}

	public void visit(TimesExpression n) {
		// PrimaryExpression
		n.f0.accept(this);
		LastExp lhs = lastExp;

		// PrimaryExpression
		n.f2.accept(this);
		LastExp rhs = lastExp;

		lastExp = new LastExp(String.format("MulS(%s %s)", printVar(lhs), printVar(rhs)), LE.Type.EXP);
	}

	public void visit(ArrayLookup n) {
		n.f0.accept(this);
		String baseAddr = printVar(lastExp);

		printNullPointer(baseAddr);

		n.f2.accept(this);
		String index = printVar(lastExp);

		printOutOfBounds(baseAddr, index);
		
		String offset = varName();
		String bytes = varName();

		printx("%s = Add(%s 1)", offset, index);
		printx("%s = MulS(%s 4)", offset, offset);
		printx("%s = Add(%s %s)", bytes, baseAddr, offset);

		lastExp = new LastExp(String.format("[%s]", bytes), LE.Type.EXP);
	}

	public void visit(ArrayLength n) {
		n.f0.accept(this);
		String baseAddr = printVar(lastExp);

		printNullPointer(baseAddr);

		lastExp = new LastExp(String.format("[%s]", baseAddr), LE.Type.EXP);
	}

	private class ClassMethod {
		ClassType classType;
		MethodType methodType;

		public ClassMethod(ClassType ct, MethodType mt) {
			classType = ct;
			methodType = mt;
		}
	}

	private ClassMethod getClassMethod(ClassType ct, String method) {
		MethodType mt = ct.methods.get(method);

		if (mt != null)
			return new ClassMethod(ct, mt);

		ClassType parent = ct.parent;

		while (parent != null) {
			mt = parent.methods.get(method);
			if (mt != null)
				return new ClassMethod(parent, mt);

			parent = parent.parent;	
		}

		return null;
	}

	public void visit(MessageSend n) {
		// PrimaryExpression
		n.f0.accept(this);
		LastExp le = lastExp;
		String callInstance = printVar(le);

		// Check if the class pointer is null
		if ( !callInstance.equals("this") )
			printNullPointer(callInstance);

		String method = n.f2.f0.toString();
		String params = callInstance;

		// Concat args
		if (n.f4.present()) {
			ExpressionList list = (ExpressionList)n.f4.node;

			list.f0.accept(this);
			params += " " + printVar(lastExp);
			
			for (Node node : list.f1.nodes) {
				node.accept(this);
				params +=  " " + printVar(lastExp);
			}
		}

		// ***
		ClassMethod cm = getClassMethod(le.classType, method);
		// ***

		String funcVar = varName();
		int methodIndex = getMethodIndex(le.classType, method);
		if (methodIndex == -1) {

			printx("%s = call :%s.%s(%s)", funcVar, cm.classType.name, method, params);			

		} else {
			String v1 = varName();

			printx("%s = [%s]", v1, callInstance);
			printx("%s = [%s+%d]", v1, v1, methodIndex*4);
			printx("%s = call %s(%s)", funcVar, v1, params);
		}

		
		XType methodRetType = cm.methodType.returnType;

		if (methodRetType.type == XType.TYPE.ID) {
			lastExp = new LastExp(funcVar, LE.Type.ID, symbolTable.classes.get(methodRetType.name));
		} else {
			lastExp = new LastExp(funcVar, LE.Type.ID);
		}
	}

	public void visit(PrimaryExpression n) {
		n.f0.accept(this);
	}

	public void visit(IntegerLiteral n) {
		lastExp = new LastExp(n.f0.toString(), LE.Type.INT);
	}

	public void visit(TrueLiteral n) {
		lastExp = new LastExp("1", LE.Type.BOOLEAN);
	}

	public void visit(FalseLiteral n) {
		lastExp = new LastExp("0", LE.Type.BOOLEAN);
	}

	public void visit(Identifier n) {

		String id = n.f0.toString();

		if (typeIdentifierScope) {
			lastExp = new LastExp(id, LE.Type.ID, symbolTable.classes.get(id));	
		} else {
			XType vt = symbolTable.scopeMethod.vars.get(id);

			// Check if id is a local var
			if (vt != null) {
				if (vt.type == XType.TYPE.ID)
					lastExp = new LastExp(id, LE.Type.ID, symbolTable.classes.get(vt.name));
				else
					lastExp = new LastExp(id, LE.Type.ID);
				return;
			}

			vt = symbolTable.scopeMethod.params.get(id);

			// Check if id is a param
			if (vt != null) {
				if (vt.type == XType.TYPE.ID)
					lastExp = new LastExp(id, LE.Type.ID, symbolTable.classes.get(vt.name));
				else
					lastExp = new LastExp(id, LE.Type.ID);
				return;
			}

			// Check if id is a class field
			int i = getFieldIndex(symbolTable.scopeClass, id);
			String field = String.format("[this+%d]", (i+1)*4);

			//*
			vt = symbolTable.scopeClass.vars.get(id);

			if (vt == null) {
				ClassType parent = symbolTable.scopeClass.parent;

				while (parent != null) {
					vt = parent.vars.get(id);

					if (vt != null)
						break;

					parent = parent.parent;
				}
			}
			//*

			
			if (vt.type == XType.TYPE.ID)
				lastExp = new LastExp(field, LE.Type.EXP, symbolTable.classes.get(vt.name));
			else
				lastExp = new LastExp(field, LE.Type.EXP);
		}
	}

	public void visit(ThisExpression n) {
		lastExp = new LastExp("this", LE.Type.ID, symbolTable.scopeClass);
	}

	public void visit(ArrayAllocationExpression n) {
		allocArray = true;

		// Expression
		n.f3.accept(this);

		String v1 = printVar(lastExp);
		String v2 = varName();

		printx("%s = call :ArrayAllocZ(%s)", v2, v1);

		lastExp = new LastExp(v2, LE.Type.ID);
	}

	public void visit(AllocationExpression n) {
		// Begin type identifier scope
		typeIdentifierScope = true;

		// Class Identifier
		n.f1.accept(this);

		// End type identifier scope
		typeIdentifierScope = false;

		String v1 = varName();
		String nullLabel = nullLabel();
		int numFields = getNumFields(symbolTable.classes.get(lastExp.string));

		printx("%s = HeapAllocZ(%s)", v1, (numFields + 1)*4);
		printNullPointer(v1);
		printx("[%s] = :vmt_%s", v1, lastExp.string);

		lastExp = new LastExp(v1, LE.Type.ID, lastExp.classType);
	}

	public void visit(NotExpression n) {
		n.f1.accept(this);

		String v = printVar(lastExp);
		String r = varName();
		String notElseLabel = notElseLabel();
		String notEndLabel = notEndLabel();

		printx("if %s goto :%s", v, notElseLabel);
		indent++;
		printx("%s = 1", r);
		printx("goto :%s", notEndLabel);
		indent--;
		printx("%s:", notElseLabel);
		printx("%s = 0", r);
		printx("%s:", notEndLabel);

		lastExp = new LastExp(r, LE.Type.ID);
	}

	public void visit(BracketExpression n) {
		n.f1.accept(this);
	}
}





















