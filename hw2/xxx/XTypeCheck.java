package xxx;

import syntaxtree.*;
import visitor.GJNoArguDepthFirst;

import java.util.*;

public class XTypeCheck extends GJNoArguDepthFirst<XType> {

	public boolean typeIdentifierScope = false;
	public SymbolTable symbolTable;

	public XTypeCheck (SymbolTable st) {
		symbolTable = st;
	}

	// *** Visitors ***

	public XType visit(NodeList n) {
	  for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
	     if (e.nextElement().accept(this) == null)
	     	return null;
	  }
	  return XType.OTHER;
	}

	public XType visit(NodeListOptional n) {
	  if ( n.present() ) {
	     for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
	        if (e.nextElement().accept(this) == null)
	        	return null;
	     }
	  }
	  return XType.OTHER;
	}

	public XType visit(NodeOptional n) {
	  if ( n.present() ) {
	     if (n.node.accept(this) == null)
	     	return null;
	  }
	  return XType.OTHER;
	}

	public XType visit(NodeSequence n) {
	  for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
	     if (e.nextElement().accept(this) == null)
	     	return null;
	  }
	  return XType.OTHER;
	}

	public XType visit(Goal n) {
		if (n.f0.accept(this) == null)
			return null;

		return n.f1.accept(this);	
	}

	public XType visit(MainClass n) {
		// Begin class scope
		symbolTable.scopeClass = symbolTable.classes.get(n.f1.f0.toString());

		// Begin method scope
		symbolTable.scopeMethod = symbolTable.scopeClass.methods.get("main");

		// Statement
		XType ret = n.f15.accept(this);

		// End method scope
		symbolTable.scopeMethod = null;

		// End class scope
		symbolTable.scopeClass = null;

		return ret;
	}

	public XType visit(TypeDeclaration n) {
		return n.f0.accept(this);
	}

	public XType visit(ClassDeclaration n) {
		// Begin class scope
		symbolTable.scopeClass = symbolTable.classes.get(n.f1.f0.toString());

		// MethodDeclaration
		XType ret = n.f4.accept(this);

		// End class scope
		symbolTable.scopeClass = null;

		return ret;
	}

	public XType visit(ClassExtendsDeclaration n) {
		// Begin class scope
		symbolTable.scopeClass = symbolTable.classes.get(n.f1.f0.toString());

		// MethodDeclaration
		XType ret = n.f6.accept(this);

		// End class scope
		symbolTable.scopeClass = null;

		return ret;
	}

	public XType visit(MethodDeclaration n) {
		// Begin method scope
		symbolTable.scopeMethod = symbolTable.scopeClass.methods.get(n.f2.f0.toString());

		// Statement
		if (n.f8.accept(this) == null)
			return null;

		// Expression
		// Checks if the return type equals the expression type
		if (n.f10.accept(this) == null)
			return null;

		XType ret = symbolTable.isSubclass(n.f10.accept(this), symbolTable.scopeMethod.returnType) ? XType.OTHER : null;

		// End method scope
		symbolTable.scopeMethod = null;

		return ret;
	}

	public XType visit(Type n) {

		typeIdentifierScope = true;

		XType ret = n.f0.accept(this);

		typeIdentifierScope = false;

		return ret;
	}

	public XType visit(ArrayType n) {
		return XType.ARRAY;
	}

	public XType visit(BooleanType n) {
		return XType.BOOLEAN;
	}

	public XType visit(IntegerType n) {
		return XType.INTEGER;
	}

	public XType visit(Statement n) {
		return n.f0.accept(this);
	}

	public XType visit(Block n) {
		return n.f1.accept(this);
	}

	// TODO: assign statement to exp?
	public XType visit(AssignmentStatement n) {
		XType idType = n.f0.accept(this);
		XType expType = n.f2.accept(this);
		
		if (idType != null && expType != null && symbolTable.isSubclass(expType, idType))
			return XType.OTHER;

		return null;
	}

	public XType visit(ArrayAssignmentStatement n) {
		XType idType = n.f0.accept(this);
		XType indexType = n.f2.accept(this);
		XType expType = n.f5.accept(this);

		if (idType == XType.ARRAY && indexType == XType.INTEGER && expType == XType.INTEGER)
			return XType.OTHER;

		return null;
	}

	public XType visit(IfStatement n) {
		XType expType = n.f2.accept(this);
		XType ifStmt = n.f4.accept(this);
		XType elseStmt = n.f6.accept(this);

		if (expType == XType.BOOLEAN && ifStmt != null && elseStmt != null)
			return XType.OTHER;

		return null;
	}

	public XType visit(WhileStatement n) {
		// Expression
		XType expType = n.f2.accept(this);
		XType stmt = n.f4.accept(this);

		if (expType == XType.BOOLEAN && stmt != null)
			return XType.OTHER;

		return null;
	}

	public XType visit(PrintStatement n) {

		// Expression
		if (n.f2.accept(this) != XType.INTEGER)
			return null;

		return XType.OTHER;
	}

	public XType visit(Expression n) {
		return n.f0.accept(this);
	}

	public XType visit(AndExpression n) {
		XType lhs = n.f0.accept(this);
		XType rhs = n.f2.accept(this);

		if (lhs == XType.BOOLEAN && rhs == XType.BOOLEAN)
			return XType.BOOLEAN;

		return null;
	}

	public XType visit(CompareExpression n) {
		XType lhs = n.f0.accept(this);
		XType rhs = n.f2.accept(this);
		
		if (lhs == XType.INTEGER && rhs == XType.INTEGER)
			return XType.BOOLEAN;

		return null;	
	}

	public XType visit(PlusExpression n) {
		XType lhs = n.f0.accept(this);
		XType rhs = n.f2.accept(this);
		
		if (lhs == XType.INTEGER && rhs == XType.INTEGER)
			return XType.INTEGER;

		return null;	
	}

	public XType visit(MinusExpression n) {
		XType lhs = n.f0.accept(this);
		XType rhs = n.f2.accept(this);
		
		if (lhs == XType.INTEGER && rhs == XType.INTEGER)
			return XType.INTEGER;

		return null;	
	}

	public XType visit(TimesExpression n) {
		XType lhs = n.f0.accept(this);
		XType rhs = n.f2.accept(this);
		
		if (lhs == XType.INTEGER && rhs == XType.INTEGER)
			return XType.INTEGER;

		return null;	
	}

	public XType visit(ArrayLookup n) {
		XType array = n.f0.accept(this);
		XType index = n.f2.accept(this);

		if (array == XType.ARRAY && index == XType.INTEGER)
			return XType.INTEGER;

		return null;	
	}

	public XType visit(ArrayLength n) {
		XType array = n.f0.accept(this);

		if (array == XType.ARRAY)
			return XType.INTEGER;

		return null;
	}

	public XType visit(MessageSend n) {
		XType priExpType = n.f0.accept(this);

		// PrimaryExpression
		if (priExpType == null || priExpType.type != XType.TYPE.ID)
			return null;

		MethodType mt = symbolTable.getMethod(priExpType.name, n.f2.f0.toString());

		if (mt == null)
			return null;

		// Check if the two methods have the same parameters
		Collection<XType> params = mt.params.values();

		if (n.f4.present()) {

			if (params == null || params.isEmpty())
				return null;

			LinkedList<XType> queue = new LinkedList<XType>(params);
			ExpressionList list = (ExpressionList)n.f4.node;

			// IMPORTANT
			if (list.f0.accept(this) == null)
				return null;

			if ( !symbolTable.isSubclass(list.f0.accept(this), queue.remove()) )
				return null;

			for (Node node : list.f1.nodes) {
				// IMPORTANT
				if (node.accept(this) == null)
					return null;

				if (queue.isEmpty() || !symbolTable.isSubclass(node.accept(this), queue.remove()))
					return null;
			}

			if ( !queue.isEmpty() )
				return null;

		} else if ( !params.isEmpty() ) {
			return null;
		}

		return mt.returnType;
	}

	public XType visit(ExpressionList n) {
		if (n.f0.accept(this) == null)
			return null;

		return n.f1.accept(this);
	}

	public XType visit(ExpressionRest n) {
		return n.f1.accept(this);
	}

	public XType visit(PrimaryExpression n) {
		return n.f0.accept(this);
	}

	public XType visit(IntegerLiteral n) {
		return XType.INTEGER;
	}

	public XType visit(TrueLiteral n) {
		return XType.BOOLEAN;
	}

	public XType visit(FalseLiteral n) {
		return XType.BOOLEAN;
	}

	public XType visit(Identifier n) {
		String identifier = n.f0.toString();

		if (typeIdentifierScope) {

			ClassType ct = symbolTable.classes.get(identifier);

			if (ct == null)
				return null;

			return new XType(XType.TYPE.ID, ct.name);

		} else {
			// Check if id is a local var
			XType idType = symbolTable.scopeMethod.vars.get(identifier);

			if (idType != null)
				return idType;

			// Check if id is a param
			idType = symbolTable.scopeMethod.params.get(identifier);

			if (idType != null)
				return idType;

			// Check if id is a class field
			idType = symbolTable.scopeClass.vars.get(identifier);

			if (idType != null)
				return idType;

			ClassType ct = symbolTable.scopeClass.parent;

			while (ct != null) {
				idType = ct.vars.get(identifier);

				if (idType != null)
					return idType;

				ct = ct.parent;
			}
		}

		// Uninitialized var
		return null;
	}

	public XType visit(ThisExpression n) {
		return new XType(XType.TYPE.ID, symbolTable.scopeClass.name);

	}

	public XType visit(ArrayAllocationExpression n) {

		if (n.f3.accept(this) != XType.INTEGER)
			return null;

		return XType.ARRAY;
	}

	public XType visit(AllocationExpression n) {

		typeIdentifierScope = true;

		XType idType = n.f1.accept(this);

		if (idType == null || idType.type != XType.TYPE.ID)
			return null;

		typeIdentifierScope = false;

		return idType;
		
	}

	public XType visit(NotExpression n) {

		if (n.f1.accept(this) != XType.BOOLEAN)
			return null;

		return XType.BOOLEAN;
	}

	public XType visit(BracketExpression n) {
		return n.f1.accept(this);
	}

}





















