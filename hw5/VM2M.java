import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VaporProgram;
import cs132.vapor.ast.VBuiltIn.Op;
import cs132.vapor.ast.*;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;

import java.util.*;

class VM2M extends VInstr.Visitor<Throwable> {

	private VaporProgram program;
	private int indent;
	private int curSPOffset;
	private int outTotal;
	private LinkedHashMap<String, Integer> stringMap;

	public static void main(String[] a) throws IOException {
		Op[] ops = {
	    Op.Add, Op.Sub, Op.MulS, Op.Eq, Op.Lt, Op.LtS,
	    Op.PrintIntS, Op.HeapAllocZ, Op.Error,
	  };
	  boolean allowLocals = false;
	  String[] registers = {
	    "v0", "v1",
	    "a0", "a1", "a2", "a3",
	    "t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7",
	    "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7",
	    "t8",
	  };
	  boolean allowStack = true;

	  VaporProgram program = null;
	  try {
	  	program = VaporParser.run(new InputStreamReader(System.in), 1, 1,
	  														java.util.Arrays.asList(ops),
	  														allowLocals, registers, allowStack);
	  }
	  catch (ProblemException e) {
	  	System.out.println(e.getMessage());
	  	System.exit(1);
	  }

	  VM2M v = new VM2M(program);
	  v.translate();
	}

	public VM2M(VaporProgram prog) {
		program = prog;
		indent = 0;
		stringMap = new LinkedHashMap<String, Integer>();
	}

	public void translate() {

		int funcIndex = 0;
		
		if (program.dataSegments.length > 0) {
			print(".data");
			print("");
		}

		// Iterate all data segments
		for (VDataSegment dataSeg : program.dataSegments) {
			print("%s:", dataSeg.ident);

			indent++;
			for (VOperand.Static data : dataSeg.values) {
				print("%s", data.toString().substring(1));
			}
			indent--;

			print("");
		}

		// Print text directive

		// Tell assembler we're in the text segment
		print(".text");
		print("");

		indent++;
		print("jal Main");
		printSyscall(10);
		indent--;

		print("");

		// Iterate all the functions
		for (VFunction func : program.functions) {

			funcIndex = func.index;

			LinkedList<VCodeLabel> labels = new LinkedList<VCodeLabel>(Arrays.asList(func.labels));

			// Print function declaration
			print("%s:", func.ident);

			curSPOffset = ((func.stack.out + func.stack.local) * 4) + 8;
			outTotal = func.stack.out;

			indent++;
			print("sw $fp -8($sp)");
			print("move $fp $sp");
			print("subu $sp $sp %d", curSPOffset);
			print("sw $ra -4($fp)"); 
			indent--;

			// Iterate all the instructions
			for (VInstr instr : func.body) {

				// Print out code label
				while (!labels.isEmpty() && labels.peek().sourcePos.line < instr.sourcePos.line) {
					print("%s:", labels.pollFirst().ident);
				}

				// Visit the instruction
				try {
					indent++;
					instr.accept(this);
					indent--;
				}
				catch(Throwable t) {
					throw new AssertionError(t);
				}
			}

			print("");
		}

		// Print built-in functions
		printPrint();
		print("");
		printError();
		print("");
		printHeapAlloc();
		print("");
		
		// Print other data
		print(".data");
		print(".align 0");
		print("_newline: .asciiz \"\\n\"");

		for (String str : stringMap.keySet()) {
			print("_str%d: .asciiz \"%s\\n\"", stringMap.get(str), str);
		}
	}

	private void print(String str, Object... args) {
		String spaces = "";
		for (int i = 0; i < indent; i++) {
			spaces += "  ";
		}
		System.out.println(spaces + String.format(str, args));
	}

	// Assume that Arg has been assigned before this is called
	private void printSyscall(int code) {
		print("li $v0 %d", code);
		print("syscall");	
	}

	private void printPrint() {
		print("_print:");
		indent++;
		printSyscall(1); // print integer
		print("la $a0 _newline");
		printSyscall(4); // print string
		printRet();
		indent--;
	}

	private void printError() {
		print("_error:");
		indent++;
		printSyscall(4); // print string
		printSyscall(10); // print exit
		indent--;
	}

	private void printHeapAlloc() {
		print("_heapAlloc:");
		indent++;
		printSyscall(9);
		printRet();
		indent--;
	}

	private void printRet() {
		print("jr $ra");
	}

	private boolean isVar(VOperand operand) {
		return operand instanceof VVarRef;
	}

	// ========================== VISITORS ========================== //

	@Override
	public void visit(VAssign a) {
		String dest = a.dest.toString();
		String src = a.source.toString();
		String instr = "";

		if (a.source instanceof VOperand.Static) {
			VOperand.Static srcOp = (VOperand.Static)a.source;
			if (srcOp instanceof VLabelRef) {
				instr = "la";
				src = src.substring(1);
			} else { // VLitInt
				instr = "li";
			}
		} else if (a.source instanceof VVarRef) {
			instr = "move";
		}

		print("%s %s %s", instr, dest, src);
	}

	@Override
	public void visit(VBranch b) {
		String branchStr = b.positive ? "bnez" : "beqz";

		print("%s %s %s", branchStr, b.value.toString(), b.target.ident);
	}

	@Override
	public void visit(VBuiltIn b) {

		VOperand arg1 = b.args[0];
		VOperand arg2 = b.args.length >= 2 ? b.args[1] : null;
		
		if (b.op == Op.Add) {

			String instr = "";
			String arg1Str = "";
			String arg2Str = "";

			if (isVar(arg1)) {

				if (isVar(arg2)) {
					instr = "addu";
				} else {
					instr = "addiu";
				}

				arg1Str = arg1.toString();
				arg2Str = arg2.toString();

			} else {

				instr = "addiu";

				if (isVar(arg2)) {
					// IMPORTANT: flip the two args
					arg1Str = arg2.toString();
					arg2Str = arg1.toString();
				} else {
					arg1Str = arg1.toString();
					arg2Str = arg2.toString();
				}

			}

			print("%s %s %s %s", instr, b.dest.toString(), arg1Str, arg2Str);

		} else if (b.op == Op.Sub) {

			String arg1Str = arg1.toString();

			if ( !isVar(arg1) ) {
				print("li $t9 %s", arg1.toString());
				arg1Str = "$t9";
			}

			print("subu %s %s %s", b.dest.toString(), arg1Str, arg2.toString());

		} else if (b.op == Op.MulS) {

			String arg1Str = arg1.toString();

			if ( !isVar(arg1) ) {
				print("li $t9 %s", arg1.toString());
				arg1Str = "$t9";
			}

			print("mul %s %s %s", b.dest.toString(), arg1Str, arg2.toString());

		} else if (b.op == Op.Eq) {

			String arg1Str = arg1.toString();

			if ( !isVar(arg1) ) {
				print("li $t9 %s", arg1.toString());
				arg1Str = "$t9";
			}

			print("seq %s %s %s", b.dest.toString(), arg1Str, arg2.toString());

		} else if (b.op == Op.Lt) {

			String instr = "";
			String arg1Str = arg1.toString();
			String arg2Str = arg2.toString();

			if (isVar(arg1)) {

				if (isVar(arg2)) {
					instr = "sltu";
				} else {
					instr = "sltiu";
				}

			} else {

				if (isVar(arg2)) {
					instr = "sgtu";

					// IMPORTANT: flip the two args
					arg1Str = arg2.toString();
					arg2Str = arg1.toString();
				} else {
					print("li $t9 %s", arg1.toString());
					arg1Str = "$t9";
					instr = "sltiu";
				}
				
			}

			print("%s %s %s %s", instr, b.dest.toString(), arg1Str, arg2Str);

		} else if (b.op == Op.LtS) {

			String instr = "";
			String arg1Str = arg1.toString();
			String arg2Str = arg2.toString();

			if (isVar(arg1)) {

				if (isVar(arg2)) {
					instr = "slt";
				} else {
					instr = "slti";
				}

			} else {

				if (isVar(arg2)) {
					instr = "sgt";

					// IMPORTANT: flip the two args
					arg1Str = arg2.toString();
					arg2Str = arg1.toString();
				} else {
					print("li $t9 %s", arg1.toString());
					arg1Str = "$t9";
					instr = "slti";
				}
				
			}

			print("%s %s %s %s", instr, b.dest.toString(), arg1Str, arg2Str);

		} else if (b.op == Op.PrintIntS) {

			String instr = "";

			if (isVar(arg1)) {
				instr = "move";
			} else {
				instr = "li";
			}

			print("%s $a0 %s", instr, arg1.toString());
			print("jal _print");

		} else if (b.op == Op.HeapAllocZ) {

			String bytes = arg1.toString();

			if (isVar(arg1)) {
				print("move $a0 %s", bytes);
			} else {
				print("li $a0 %s", bytes);
			}

			print("jal _heapAlloc");
			print("move %s $v0", b.dest.toString());

		} else if (b.op == Op.Error) {

			String argStr = ((VLitStr)arg1).value;
			String dataStr = "_str";

			if ( !stringMap.containsKey(argStr) )
				stringMap.put(argStr, stringMap.size());

			dataStr += stringMap.get(argStr);

			print("la $a0 %s", dataStr);
			print("j _error");

		} 
	}

	@Override
	public void visit(VCall c) {
		String instr = "";
		String addr = c.addr.toString();

		if (c.addr instanceof VAddr.Label) {
			instr = "jal";
			addr = addr.substring(1);
		} else {
			instr = "jalr";
		}

		print("%s %s", instr, addr);
	}

	@Override
	public void visit(VGoto g) {
		print("j %s", g.target.toString().substring(1));
	}

	@Override
	public void visit(VMemRead r) {
		int offset = 0;
		String base = "";

		if (r.source instanceof VMemRef.Stack) {

			VMemRef.Stack stack = (VMemRef.Stack)r.source;
			offset = stack.index * 4;

			if (stack.region == VMemRef.Stack.Region.In) {
				base = "$fp";
			} else if (stack.region == VMemRef.Stack.Region.Local) {
				offset += (outTotal * 4);
				base = "$sp";
			}

		} else { // VMemRef.Global
			VMemRef.Global global = (VMemRef.Global)r.source;
			offset = global.byteOffset;
			base = global.base.toString();
		}

		print("lw %s %d(%s)", r.dest.toString(), offset, base);
	}

	@Override
	public void visit(VMemWrite w) {
		String src = "";
		int offset = 0;
		String base = "";

		// Destination

		if (w.dest instanceof VMemRef.Stack) {

			VMemRef.Stack stack = (VMemRef.Stack)w.dest;
			offset = stack.index * 4;

			if (stack.region == VMemRef.Stack.Region.Out) {
				// Out stack
				base = "$sp";
			} else if (stack.region == VMemRef.Stack.Region.Local) {
				// Local stack
				offset += (outTotal * 4);
				base = "$sp";
			}

		} else { // VMemRef.Global
			VMemRef.Global global = (VMemRef.Global)w.dest;
			offset = global.byteOffset;
			base = global.base.toString();
		}

		// Source
		if (w.source instanceof VOperand.Static) {
			VOperand.Static srcOp = (VOperand.Static)w.source;
			if (srcOp instanceof VLabelRef) {

				//print("bitch");
				//print(w.source.toString());
				//print(w.source.toString().substring(1));
				//print("bitch");

				print("la $v0 %s", w.source.toString().substring(1));
				src = "$v0";
			} else { // VLitInt
				print("la $v0 %s", w.source.toString());
				src = "$v0";
			}
		} else if (w.source instanceof VVarRef) {
			src = w.source.toString();
		}

		print("sw %s %d(%s)", src, offset, base);
	}

	@Override
	public void visit(VReturn r) {
		print("lw $ra -4($fp)");
		print("lw $fp -8($fp)");
		print("addu $sp $sp %d", curSPOffset);
		printRet();
	}
}
























