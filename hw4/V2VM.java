import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VaporProgram;
import cs132.vapor.ast.VBuiltIn.Op;
import cs132.vapor.ast.*;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;

import java.util.*;

class V2VM extends VInstr.Visitor<Throwable> {

	private ArrayList<HashMap<String,String>> regMaps;
	private ArrayList<Integer> localTotalList;
	private ArrayList<Integer> outTotalList; 
	private VaporProgram program;
	private int indent = 0;
	private int curFuncIndex = 0;

	public static void main(String[] a) throws IOException {
		Op[] ops = {
	    Op.Add, Op.Sub, Op.MulS, Op.Eq, Op.Lt, Op.LtS,
	    Op.PrintIntS, Op.HeapAllocZ, Op.Error,
	  };
	  boolean allowLocals = true;
	  String[] registers = null;
	  boolean allowStack = false;

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

	  LinearScan regAlloc = new LinearScan(program);

	  V2VM v = new V2VM(program);
	  v.regMaps = regAlloc.getRegisterMaps();
	  v.localTotalList = regAlloc.getLocalTotalList();
	  v.outTotalList = regAlloc.getOutTotalList();

	  v.translate();
	}

	public V2VM(VaporProgram prog) {
		program = prog;		
	}

	private void translate() {
		// Print data segments
		for (VDataSegment ds : program.dataSegments) {
			print("%s %s", ds.mutable ? "var" : "const", ds.ident);

			if (ds.values.length > 0) {
				indent++;
				for (VOperand.Static operand : ds.values) {
					print(operand.toString());
				}
				indent--;
			}

			print("");
		}

		// Print all the functions in the program
		for (VFunction func : program.functions) {
			curFuncIndex = func.index;

			int inSize = func.params.length > 4 ? func.params.length - 4 : 0;
			int outSize = outTotalList.get(curFuncIndex);
			int localSize = 0;

			// Calculate the number of local spaces need to be allocated
			for (String reg : regMaps.get(curFuncIndex).values()) {
				if (reg.charAt(1) == 's') {
					localSize++;
				}
			}
			localSize += localTotalList.get(curFuncIndex);

			print("func %s [in %d, out %d, local %d]", func.ident, inSize, outSize, localSize);

			// Print stored s registers
			indent++;
			printStoredCalleeRegs(false);
			indent--;

			// Print parameters
			int paramIndex = 0;
			indent++;
			for (VVarRef.Local param : func.params) {
				String r = getReg(param.toString());

				// Only print the parameters that are used within the function
				if (r != null) {
					if (!isReg(r)) {
						print("$v0 = %s", getParamReg(paramIndex));
						print("%s = $v0", r);
					} else {
						print("%s = %s", r, getParamReg(paramIndex));
					}
				}

				// Increment param index
				paramIndex++;
			}
			indent--;

			LinkedList<VCodeLabel> labels = new LinkedList<VCodeLabel>(Arrays.asList(func.labels));

			// Print all the instructions in the function
			for (VInstr instr : func.body) {
				while (!labels.isEmpty() && (labels.peek().sourcePos.line < instr.sourcePos.line)) {
					print("%s:", labels.pop().ident);
				}

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
	}

	private void print(String str, Object... args) {
		String indentStr = "";
		for (int i = 0; i < indent; i++) {
			indentStr += "  ";
		}
		System.out.println(indentStr + String.format(str, args));
	}

	private void printStoredCalleeRegs(boolean restore) {
		int localIndex = localTotalList.get(curFuncIndex);
		for (String reg : regMaps.get(curFuncIndex).values()) {
			if (reg.charAt(1) == 's') {
				if (restore)
					print("%s = local[%d]", reg, localIndex++);
				else
					print("local[%d] = %s", localIndex++, reg);
			}	
		}
	}

	private String getParamReg(int index) {
		switch (index) {
			case 0:
				return "$a0";

			case 1:
				return "$a1";

			case 2:
				return "$a2";

			case 3:
				return "$a3";

			default:
				return String.format("in[%d]", index - 4);
		}
	}

	private String getReg(String reg) {
		return regMaps.get(curFuncIndex).get(reg);
	}

	private boolean isReg(String str) {
		return str.charAt(0) == '$';
	}

	private boolean isVar(VOperand o) {
		return o instanceof VVarRef;
	}

	private boolean isVar(VVarRef v) {
		return v instanceof VVarRef.Local;
	}

	private boolean isVar(VAddr a) {
		return a instanceof VAddr.Var;
	}

	// ========================== VISITORS ========================== //

	@Override
	public void visit(VAssign a) {
		String r = getReg(a.dest.toString());
		String src = a.source.toString();
		String r2 = isVar(a.source) ? getReg(src) : src;
		if (isVar(a.source) && !isReg(r2)) {
			print("$v1 = %s", r2);
			r2 = "$v1";
		}
		
		print("%s = %s", r, r2);
	}

	@Override
	public void visit(VBranch b) {
		String ifStr = b.positive ? "if" : "if0";
		String r = getReg(b.value.toString());
		if (!isReg(r)) {
			print("$v0 = %s", r);
			r = "$v0";
		}

		print("%s %s goto :%s", ifStr, r, b.target.ident);
	}

	@Override
	public void visit(VBuiltIn c) {
		String args = "";
		/*for (VOperand operand : c.args) {
			args += operand.toString();
			args += " ";
		}*/

		if (c.args.length == 1) {
			String arg0 = c.args[0].toString();
			String r = isVar(c.args[0]) ? getReg(arg0) : arg0;
			if (isVar(c.args[0]) && !isReg(r)) {
				print("$v0 = %s", r);
				r = "$v0";
			}

			args = r;

		} else if (c.args.length == 2) {
			String arg0 = c.args[0].toString();
			String r = isVar(c.args[0]) ? getReg(arg0) : arg0;
			if (isVar(c.args[0]) && !isReg(r)) {
				print("$v0 = %s", r);
				r = "$v0";
			}

			String arg1 = c.args[1].toString();
			String r2 = isVar(c.args[1]) ? getReg(arg1) : arg1;
			if (isVar(c.args[1]) && !isReg(r2)) {
				print("$v1 = %s", r2);
				r2 = "$v1";
			}

			args = String.format("%s %s", r, r2);
		}

		if (c.dest == null) {
			print("%s(%s)", c.op.name, args);
		} else if (isVar(c.dest)) {
			VVarRef.Local dest = (VVarRef.Local)c.dest;
			//print("%s = %s(%s)", dest.ident, c.op.name, args.trim());

			String r = getReg(c.dest.toString());
			if (!isReg(r)) {
				print("$v0 = %s(%s)", c.op.name, args);
				print("%s = $v0", r);
			} else {
				print("%s = %s(%s)", r, c.op.name, args);
			}
		}
	}

	private String getArgVal(VOperand operand) {
		return isVar(operand) ? getReg(operand.toString()) : operand.toString();
	}

	@Override
	public void visit(VCall c) {
		String args = "";
		for (VOperand operand : c.args) {
			args += operand.toString();
			args += " "; 
		}
		//print("%s = call %s(%s)", c.dest.toString(), c.addr.toString(), args.trim());

		switch (c.args.length) {
			case 0:
				break;

			case 1:
				print("$a0 = %s", getArgVal(c.args[0]));
				break;

			case 2:
				print("$a0 = %s", getArgVal(c.args[0]));
				print("$a1 = %s", getArgVal(c.args[1]));
				break;

			case 3:
				print("$a0 = %s", getArgVal(c.args[0]));
				print("$a1 = %s", getArgVal(c.args[1]));
				print("$a2 = %s", getArgVal(c.args[2]));
				break;

			default:
				print("$a0 = %s", getArgVal(c.args[0]));
				print("$a1 = %s", getArgVal(c.args[1]));
				print("$a2 = %s", getArgVal(c.args[2]));
				print("$a3 = %s", getArgVal(c.args[3]));
				break;
		}

		if (c.args.length > 4) {
			int outIndex = 0;
			for (int i = 4; i < c.args.length; i++) {

				String r = isVar(c.args[i]) ? getReg(c.args[i].toString()) : c.args[i].toString();
				if (isVar(c.args[i]) && !isReg(r)) {
					print("$v0 = %s", r);
					r = "$v0";
				}
				print("out[%d] = %s", outIndex++, getArgVal(c.args[i]));
			}
		}

		String r = isVar(c.addr) ? getReg(c.addr.toString()) : c.addr.toString();
		if (isVar(c.addr) && !isReg(r)) {
			print("$v0 = %s", r);
			r = "$v0";
		}
		print("call %s", r);
		print("%s = $v0", getReg(c.dest.toString()));
	}

	@Override
	public void visit(VGoto g) {
		print("goto %s", g.target.toString());	
	}

	@Override
	public void visit(VMemRead re) {
		if (isVar(re.dest)) {
			VVarRef.Local dest = (VVarRef.Local)re.dest;
			VMemRef.Global src = (VMemRef.Global)re.source;

			//print("%s = [%s+%d]", dest.ident, src.base.toString(), src.byteOffset);

			String r2 = getReg(src.base.toString());
			if (!isReg(r2)) {
				print("$v1 = %s", r2);
				r2 = "$v1";
			}			

			String r = getReg(dest.ident);
			if (!isReg(r)) {
				print("$v0 = [%s+%d]", r2, src.byteOffset);
				print("%s = $v0", r);
			} else {
				print("%s = [%s+%d]", r, r2, src.byteOffset);
			}
		}
	}

	@Override
	public void visit(VMemWrite w) {
		VMemRef.Global dest = (VMemRef.Global)w.dest;

		//print("[%s+%d] = %s", dest.base.toString(), dest.byteOffset, w.source.toString());

		String r = getReg(dest.base.toString());
		if (!isReg(r)) {
			print("$v0 = %s", r);
			r = "$v0";
		}

		String src = w.source.toString();
		String r2 = isVar(w.source) ? getReg(src) : src;
		if (isVar(w.source) && !isReg(r2)) {
			print("$v1 = %s", r2);
			r2 = "$v1";
		} 

		print("[%s+%d] = %s", r, dest.byteOffset, r2);	
	}

	@Override
	public void visit(VReturn re) {
		//print("ret %s", r.value != null ? r.value.toString() : "");

		if (re.value != null) {

			String r = isVar(re.value) ? getReg(re.value.toString()) : re.value.toString();

			if (isVar(re.value) && !isReg(r)) {
				print("$v0 = %s", r);
				r = "$v0";
			}

			print("$v0 = %s", r);
		}

		printStoredCalleeRegs(true);
		
		print("ret");
	}
}
























