import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VaporProgram;
import cs132.vapor.ast.VBuiltIn.Op;
import cs132.vapor.ast.*;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;

import java.util.*;
import java.lang.*;

class LinearScan extends VInstr.Visitor<Throwable> {

	/**
	 * IMPORTANT: startPoint and endPoint correspond to
	 * the edges between instructions
	 */

	private class VarNode {
		String var;
		String reg;
		boolean isParam;
		int startPoint;
		int endPoint;

		boolean isAboveCall;
		boolean isAcrossCall;

		HashSet<String> aboveLabels;
		HashSet<String> acrossLabels;

		public VarNode(String v) {
			var = v;
			reg = "";
			isParam = false;
			startPoint = 0;
			endPoint = 0;

			isAboveCall = false;
			isAcrossCall = false;

			aboveLabels = new HashSet<String>();
			acrossLabels = new HashSet<String>();
		}
	}

	// Live Ranges
	private ArrayList<HashMap<String, VarNode>> liveRangeMaps;
	private ArrayList<LinkedList<VarNode>> liveRangeLinkedLists;
	private ArrayList<Integer> localIndexList;
	private ArrayList<Integer> outTotalList;

	private LinkedList<String> freeRegs;

	// Caller Saved Registers
	private LinkedList<String> callerSavedRegs;

	// Callee Saved Registers
	private LinkedList<String> calleeSavedRegs;

	private int curFuncIndex;
	private VaporProgram program;

	private int freeCalleeRegsTotal;
	private int regsTotal;

	public LinearScan(VaporProgram prog) {
		program = prog;

		liveRangeMaps = new ArrayList<HashMap<String, VarNode>>();
		liveRangeLinkedLists = new ArrayList<LinkedList<VarNode>>();
		localIndexList = new ArrayList<Integer>();
		outTotalList = new ArrayList<Integer>();

		freeRegs = new LinkedList<String>();

		// Initialize registers to allocate
		callerSavedRegs = new LinkedList<String>(Arrays.asList("$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8"));
		calleeSavedRegs = new LinkedList<String>(Arrays.asList("$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7"));

		curFuncIndex = 0;
		freeCalleeRegsTotal = calleeSavedRegs.size();
		regsTotal = callerSavedRegs.size() + calleeSavedRegs.size();
	}

	public ArrayList<HashMap<String, String>> getRegisterMaps() {

		analyze();

		//printLiveRanges();

		allocateReg();

		//printRegMaps();

		ArrayList<HashMap<String,String>> regMaps = new ArrayList<HashMap<String,String>>(liveRangeMaps.size());

		// Create register map
		for (HashMap<String,VarNode> liveRangeMap : liveRangeMaps) {
			HashMap<String,String> regMap = new HashMap<String,String>();
			for (VarNode varNode : liveRangeMap.values()) {
				regMap.put(varNode.var, varNode.reg);
			}
			regMaps.add(regMap);
		}

		return regMaps;
	}

	public ArrayList<Integer> getLocalTotalList() {
		return localIndexList;
	}

	public ArrayList<Integer> getOutTotalList() {
		return outTotalList;
	}

	private void analyze() {
	  // Iterate all the functions in the program
	  for (VFunction f : program.functions) {
	  	liveRangeMaps.add(f.index, new HashMap<String, VarNode>());
	  	liveRangeLinkedLists.add(f.index, new LinkedList<VarNode>());
	  	localIndexList.add(f.index, 0);
	  	outTotalList.add(f.index, 0);

	  	// Iterate all the instructions in the function
	  	curFuncIndex = f.index;

	  	// Create VarNodes whose startPoint = endPoint = 0
	  	// for all the parameters
	  	initLiveRangeForParams(f.params);

			// Put the labels into a linked list so we can remove them
	  	// after adding them to the VarNodes
	  	LinkedList<VCodeLabel> labels = new LinkedList<VCodeLabel>(Arrays.asList(f.labels));

	  	for (VInstr curInstr : f.body) {
	  		try {
	  			// Add label to all VarNodes occurring before the label
	  			while (!labels.isEmpty() && labels.peek().sourcePos.line < curInstr.sourcePos.line) {
	  				String label = labels.pop().ident;
	  				for (VarNode varNode : liveRangeLinkedLists.get(curFuncIndex)) {
	  						varNode.aboveLabels.add(label);
	  				}
	  			}

	  			// Visit the instructions
	  			curInstr.accept(this);
	  		}
	  		catch(Throwable t) {
	  			//System.out.println(e.getMessage());
	  			//System.exit(1);
	  			// TODO: not sure what I am doing here!
	  			throw new AssertionError(t);
	  		}
	  	}
	  }
	}

	private void printx(String str, Object... args) {
		System.out.println(String.format(str, args));
	}

	private void initLiveRangeForParams(VVarRef.Local[] params) {
		// Get the live range map and linked list for the currently visited function
		HashMap<String,VarNode> liveRangeMap = liveRangeMaps.get(curFuncIndex);
		LinkedList<VarNode> liveRangeLinkedList = liveRangeLinkedLists.get(curFuncIndex);

		for (VVarRef.Local param : params) {
			VarNode varNode = new VarNode(param.ident);
			varNode.startPoint = param.sourcePos.line;
			varNode.endPoint = param.sourcePos.line;

			// Put varNode in map
			liveRangeMap.put(param.ident, varNode);

			// Add varNode to the end of linked list
			liveRangeLinkedList.add(varNode);
		}
	}

	private void addDefRange(String var, int lineNum) {
		// Get the live range map and linked list for the currently visited function
		HashMap<String,VarNode> liveRangeMap = liveRangeMaps.get(curFuncIndex);
		LinkedList<VarNode> liveRangeLinkedList = liveRangeLinkedLists.get(curFuncIndex);

		if ( !liveRangeMap.containsKey(var) ) {
			VarNode varNode = new VarNode(var);
			varNode.startPoint = lineNum;

			// Put varNode in map
			liveRangeMap.put(var, varNode);

			// Add varNode to the end of linked list
			liveRangeLinkedList.add(varNode);
		} else {
			VarNode varNode = liveRangeMap.get(var);

			// Note that varNode is above a call and is used below it
			// thus it is live across the call
			if (varNode.isAboveCall)
				varNode.isAcrossCall = true;

			// Note that varNode is above a code label and is used below it
			// thus it is live across the label
			varNode.acrossLabels.addAll(varNode.aboveLabels);
			varNode.aboveLabels.clear();
		}
	}

	private void addUseRange(String var, int lineNum) {
		// Get the live range map and linked list for the currently visited function
		HashMap<String,VarNode> liveRangeMap = liveRangeMaps.get(curFuncIndex);
		LinkedList<VarNode> liveRangeLinkedList = liveRangeLinkedLists.get(curFuncIndex);
		VarNode varNode = null;

		if ( !liveRangeMap.containsKey(var) ) {
			// varNode for the var does not exist yet

			varNode = new VarNode(var);
			varNode.isParam = true; // IMPORTANT

			if (liveRangeMap.isEmpty()) {
				varNode.startPoint = lineNum - 1;
			} else {
				VarNode firstVarNode = liveRangeLinkedList.getFirst();
				varNode.startPoint = firstVarNode.isParam ? firstVarNode.startPoint : firstVarNode.startPoint - 1;
			}

			// Put varNode in map
			liveRangeMap.put(var, varNode);

			// Add varNode to the front of linked list
			liveRangeLinkedList.addFirst(varNode);

		} else {
			varNode = liveRangeMap.get(var);

			// Note that varNode is above a call and is used below it
			// thus it is live across the call
			if (varNode.isAboveCall)
				varNode.isAcrossCall = true;

			// Note that varNode is above a code label and is used below it
			// thus it is live across the label
			varNode.acrossLabels.addAll(varNode.aboveLabels);
			varNode.aboveLabels.clear();
		}

		varNode.endPoint = lineNum - 1;
	}

	private void printLiveRanges() {
		int i = 0;
		for (LinkedList<VarNode> liveRangeLinkedList : liveRangeLinkedLists) {

			printx("Func %d", i++);
			for (VarNode varNode : liveRangeLinkedList) {
				printx("Var: %s [%d:%d]", varNode.var, varNode.startPoint, varNode.endPoint);
			}
			printx("");
		}
	}

	private void printRegMaps() {
		for (LinkedList<VarNode> liveRangeLinkedList : liveRangeLinkedLists) {
			for (VarNode varNode : liveRangeLinkedList) {
				printx("Var %s => %s", varNode.var, varNode.reg);
			}
			printx("");
		}
	}

	/**
	 * Linear Scan Register Allocation Algorithm
	 */

	private void allocateReg() {
		LinkedList<VarNode> active = new LinkedList<VarNode>();
		
		int index = 0;
		for (LinkedList<VarNode> liveRangeLinkedList : liveRangeLinkedLists) {
			// IMPORTANT
			curFuncIndex = index++;

			for (VarNode varNode : liveRangeLinkedList) {
				expireOldIntervals(varNode, active);
				if (active.size() == regsTotal || (varNode.isAcrossCall && freeCalleeRegsTotal <= 0)) {
					spillAtInterval(varNode, active);
				} else {
					varNode.reg = getFreeReg(varNode.isAcrossCall);
					addActive(varNode, active);
				}
			}
		}
	}

	private void expireOldIntervals(VarNode varNode, LinkedList<VarNode> active) {
		Iterator<VarNode> itr = active.iterator();
		while (itr.hasNext()) {
			VarNode vn = itr.next();
			if (vn.endPoint >= varNode.startPoint) {
				return;
			}

			freeReg(vn.reg);
			itr.remove();
		}
	}

	private void spillAtInterval(VarNode varNode, LinkedList<VarNode> active) {
		VarNode lastVarNode = active.getLast();

		// Get the new index of local
		int newIndex = localIndexList.get(curFuncIndex);
		localIndexList.set(curFuncIndex, newIndex + 1);

		if (lastVarNode.endPoint > varNode.endPoint) {
			varNode.reg = lastVarNode.reg;

			// Remove lastVarNode from active
			active.remove(lastVarNode);
			addActive(varNode, active);

			// Assign lastVarNode to stack
			lastVarNode.reg = String.format("local[%d]", newIndex);

		} else {
			// Assign varNode to stack
			varNode.reg = String.format("local[%d]", newIndex);
		}
	}

	private String getFreeReg(boolean onlyCallee) {
		if (onlyCallee) {
			for (String reg : freeRegs) {
				if (reg.charAt(1) == 's') {
					freeRegs.remove(reg);
					freeCalleeRegsTotal--;
					return reg;
				}
			}

			// If there are no s registers in freeRegs
			// find one in calleeSavedRegs
			freeCalleeRegsTotal--;
			return calleeSavedRegs.pollFirst();
		}

		// Get r registers if any, otherwise get s registers
		if ( !freeRegs.isEmpty() ) {
			String reg = freeRegs.pollFirst();
			if (reg.charAt(1) == 's')
				freeCalleeRegsTotal--;
			return reg;
		} else {
			if ( !callerSavedRegs.isEmpty() ) {
				return callerSavedRegs.pollFirst();
			} else {
				freeCalleeRegsTotal--;
				return calleeSavedRegs.pollFirst();
			}
		}
	}

	private void freeReg(String reg) {
		if (reg.charAt(1) == 's')
			freeCalleeRegsTotal++;

		// Add the register back to the linked list of free registers
		freeRegs.add(reg);
	}

	// Add a varNode to active in order of increasing endPoint
	private void addActive(VarNode varNode, LinkedList<VarNode> active) {
		int i = 0;
		for (VarNode vn : active) {
			if (vn.endPoint == varNode.endPoint) {
				active.add(i, varNode);
				return;
			}

			i++;
		}

		// No varNode has the same endPoint, 
		// add it to the end of active
		active.add(varNode);
	}

	// ========================== VISITORS ========================== //

	@Override
	public void visit(VAssign a) {
		int lineNum = a.sourcePos.line;

		// Dest (LHS)
		if (a.dest instanceof VVarRef.Local) {
			VVarRef.Local dest = (VVarRef.Local)a.dest;
			addDefRange(dest.ident, lineNum);
		}

		// Source (RHS)
		if (a.source instanceof VLitStr) {

			VLitStr litStrSrc = (VLitStr)a.source;
			addUseRange(litStrSrc.value, lineNum);

		} else if (a.source instanceof VVarRef) {

			VVarRef varRefSrc = (VVarRef)a.source;
			if (varRefSrc instanceof VVarRef.Local) {
				VVarRef.Local varRefLocalSrc = (VVarRef.Local)varRefSrc;
				addUseRange(varRefLocalSrc.ident, lineNum);
			}

		}
	}

	@Override
	public void visit(VBranch b) {
		int lineNum = b.sourcePos.line;

		// Update VarNodes who are live across the "if target label"
		// so that their endPoint is the edge to the "if instruction"
		String label = b.target.ident; 
		for (VarNode varNode : liveRangeLinkedLists.get(curFuncIndex)) {
			if (varNode.acrossLabels.contains(label)) {
				varNode.endPoint = lineNum - 1;

				if (varNode.isAboveCall)
					varNode.isAcrossCall = true;
			}
		}

		// Conditional value
		if (b.value instanceof VVarRef) {
			VVarRef varRefVal = (VVarRef)b.value;
			if (varRefVal instanceof VVarRef.Local) {
				VVarRef.Local varRefLocalVal = (VVarRef.Local)varRefVal;
				addUseRange(varRefLocalVal.ident, lineNum);
			}
		}
	}

	@Override
	public void visit(VBuiltIn c) {
		int lineNum = c.sourcePos.line;

		if (c.dest instanceof VVarRef) {
			VVarRef.Local dest = (VVarRef.Local)c.dest;
			addDefRange(dest.ident, lineNum);
		}

		for (VOperand o : c.args) {	
			if (o instanceof VVarRef) {
				VVarRef varRef = (VVarRef)o;

				if (varRef instanceof VVarRef.Local) {
					VVarRef.Local varRefLocal = (VVarRef.Local)varRef;
					addUseRange(varRefLocal.ident, lineNum);
				}
			}
		}
	}

	@Override
	public void visit(VCall c) {
		int lineNum = c.sourcePos.line;

		// Addr
		if (c.addr instanceof VAddr.Var) {
			VAddr.Var addr = (VAddr.Var)c.addr;
			if (addr.var instanceof VVarRef.Local) {
				VVarRef.Local varRefLocalAddr = (VVarRef.Local)addr.var;
				addUseRange(varRefLocalAddr.ident, lineNum);
			}
		}

		// Args
		for (VOperand arg : c.args) {
			if (arg instanceof VVarRef) {
				VVarRef varRefArg = (VVarRef)arg;
				if (varRefArg instanceof VVarRef.Local) {
					VVarRef.Local varRefLocalArg = (VVarRef.Local)varRefArg;
					addUseRange(varRefLocalArg.ident, lineNum);
				}
			}
		}

		// Mark all the variables above this call
		for (VarNode varNode : liveRangeLinkedLists.get(curFuncIndex)) {
			varNode.isAboveCall = true;
		}

		// Dest
		addDefRange(c.dest.ident, lineNum);

		// Get the number of arguments for this function
		if (c.args.length > 4)
			outTotalList.set(curFuncIndex, Math.max(c.args.length-4, outTotalList.get(curFuncIndex)));
	}

	@Override
	public void visit(VGoto g) {
		int lineNum = g.sourcePos.line;

		// TODO: get target from var
		if (g.target instanceof VAddr.Var) {
			VAddr.Var addr = (VAddr.Var)g.target;
			if (addr.var instanceof VVarRef.Local) {
				VVarRef.Local varRefLocalAddr = (VVarRef.Local)addr.var;
				addUseRange(varRefLocalAddr.ident, lineNum);
			}
		} else if (g.target instanceof VAddr.Label) {
			/*VAddr.Label addr = (VAddr.Label)g.target;
			if (addr.label instanceof VLabelRef) {
				VLabelRef labelRef = (VLabelRef)addr.label;
			}*/

			// Update VarNodes who are live across the "if target label"
			// so that their endPoint is the edge to the "if instruction"
			String label = g.target.toString().substring(1); 
			for (VarNode varNode : liveRangeLinkedLists.get(curFuncIndex)) {
				if (varNode.acrossLabels.contains(label)) {
					varNode.endPoint = lineNum - 1;

					if (varNode.isAboveCall)
						varNode.isAcrossCall = true;
				}
			}
		}
	}

	@Override
	public void visit(VMemRead r) {
		int lineNum = r.sourcePos.line;

		// Dest
		if (r.dest instanceof VVarRef.Local) {
			VVarRef.Local varRefLocalDest = (VVarRef.Local)r.dest;
			addDefRange(varRefLocalDest.ident, lineNum);
		}

		// Source
		if (r.source instanceof VMemRef.Global) {
			VMemRef.Global global = (VMemRef.Global)r.source;
			if (global.base instanceof VAddr.Var) {
				VAddr.Var addr = (VAddr.Var)global.base;
				if (addr.var instanceof VVarRef.Local) {
					VVarRef.Local varRefLocalAddr = (VVarRef.Local)addr.var;
					addUseRange(varRefLocalAddr.ident, lineNum);
				}
			}
		}
	}

	@Override
	public void visit(VMemWrite w) {
		int lineNum = w.sourcePos.line;

		// Dest
		if (w.dest instanceof VMemRef.Global) {
			VMemRef.Global global = (VMemRef.Global)w.dest;
			if (global.base instanceof VAddr.Var) {
				VAddr.Var addr = (VAddr.Var)global.base;
				if (addr.var instanceof VVarRef.Local) {
					VVarRef.Local varRefLocalAddr = (VVarRef.Local)addr.var;
					addUseRange(varRefLocalAddr.ident, lineNum);
				}
			}
		}

		// Source
		if (w.source instanceof VVarRef) {
			VVarRef source = (VVarRef)w.source;
			if (source instanceof VVarRef.Local) {
				VVarRef.Local varRefLocalSrc = (VVarRef.Local)source;
				addUseRange(varRefLocalSrc.ident, lineNum);
			}
		}
	}

	@Override
	public void visit(VReturn r) {
		if (r.value instanceof VVarRef) {
			VVarRef source = (VVarRef)r.value;
			if (source instanceof VVarRef.Local) {
				VVarRef.Local varRefLocalSrc = (VVarRef.Local)source;
				addUseRange(varRefLocalSrc.ident, r.sourcePos.line);
			}
		}
	}
}



























