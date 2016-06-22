package xxx;

import syntaxtree.*;
import visitor.GJNoArguDepthFirst;

import java.util.*;

public class SymbolTable extends GJNoArguDepthFirst<Boolean> {

	public ClassType scopeClass = null;
	public MethodType scopeMethod = null;
	public HashMap<String, ClassType> classes = new HashMap<String, ClassType>();

	public boolean isSubclass(XType child, XType parent) {
		if (child.type == XType.TYPE.ID && parent.type == XType.TYPE.ID) {
			while (!child.equals(parent)) {

				ClassType ct = classes.get(child.name);

				if (ct.parent == null)
					return false;

				child = new XType(XType.TYPE.ID, ct.parent.name);
			}

			return true;
		} 

		return child.equals(parent);
	}

	public MethodType getMethod(String classId, String methodId) {

		ClassType ct = classes.get(classId);
		if (ct == null)
			return null;

		MethodType mt = ct.methods.get(methodId);
		if (mt != null)
			return mt;

		ClassType parent = ct.parent;

		while (parent != null) {

			mt = parent.methods.get(methodId);
			if (mt != null)
				return mt;

			parent = parent.parent;
		}

		return null;
	}

	public XType getType(Type t) {

		switch (t.f0.which) {
			case 0: 
				return XType.ARRAY;

			case 1:
				return XType.BOOLEAN;

			case 2:
				return XType.INTEGER;

			case 3:
				String className = ((Identifier)t.f0.choice).f0.toString();
				XType type = new XType(XType.TYPE.ID, className);

				// Add identifier to table if 
				// does not exist yet
				if (classes.get(className) == null) {
					ClassType ct = new ClassType(className);
					ct.found = false;

					// Add identifier to table
					classes.put(className, ct);
				}
				return type;

			default:
				return null;
		}
	}

	public ClassType addClass(String identifier) {
		ClassType ct = classes.get(identifier);

		if (ct == null) {
			// Id has not been added to the table, so add it
			ct = new ClassType(identifier);
			classes.put(identifier, ct);
		} else if (!ct.found) {
			// The class is now initialized
			ct.found = true;
		} else {
			// Initialize the same class more than once
			return null;
		}

		return ct;
	}

	public ClassType addClass(String identifier, String parentIdentifier) {
		ClassType ct = addClass(identifier);

		if (ct == null)
			return null;

		// Add parent class to table
		ClassType pct = classes.get(parentIdentifier);
		if (pct == null) {
			pct = new ClassType(parentIdentifier);
			pct.found = false;

			classes.put(parentIdentifier, pct);
		}

		ct.parent = pct;

		return ct;
	}

	public MethodType addMethod(String identifier, XType type) {
		MethodType mt = scopeClass.methods.get(identifier);

		if (mt != null)
			return null;

		mt = new MethodType(identifier, type);
		scopeClass.methods.put(identifier, mt);

		return mt;
	}

	public XType addVar(String identifier, XType type) {

		if (scopeMethod != null) {
			XType t = scopeMethod.vars.get(identifier);
			XType t2 = scopeMethod.params.get(identifier);

			if (t2 != null)
				return null;

			if (t != null)
				return null;

			scopeMethod.vars.put(identifier, type);
			return type;
		} 

		XType t = scopeClass.vars.get(identifier);

		if (t != null)
			return null;

		scopeClass.vars.put(identifier, type);
		return type;
	}

	public XType addParam(String identifier, XType type) {

		XType t = scopeMethod.params.get(identifier);

		if (t != null)
			return null;

		scopeMethod.params.put(identifier, type);
		return type;
	}

	// ***

	@Override
	public Boolean visit(NodeList n) {
	  for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
	     if (e.nextElement().accept(this) == null)
	     	return null;
	  }
	  return true;
	}

	@Override
	public Boolean visit(NodeListOptional n) {
	  if ( n.present() ) {
	     for ( Enumeration<Node> e = n.elements(); e.hasMoreElements(); ) {
	        if (e.nextElement().accept(this) == null)
	   			return null;
	     }
	  }
	  return true;
	}

	@Override
	public Boolean visit(NodeOptional n) {
	  if ( n.present() )
	     return n.node.accept(this);
	  else
	     return true;
	}

	public boolean hasCycles() {

		for (ClassType ct : classes.values()) {

			int i = 0;
			int numClasses = classes.size() - 1;
			ClassType parent = ct.parent;

			while (parent != null && i < numClasses) {
				if (ct == parent)
					return true;

				parent = parent.parent;

				i++;
			}

		}

		return false;
	}

	public boolean hasOverload() {

		for (ClassType ct : classes.values()) {

			ClassType parent = ct.parent;

			while (parent != null) {

				for (MethodType mt : ct.methods.values()) {
					MethodType pmt = parent.methods.get(mt.name);
					if (pmt != null) {
						// There is a method with the same name in a class'
						// parent or parent's parent and so forth
						Iterator<XType> itr = pmt.params.values().iterator();

						for (XType t : mt.params.values()) {
							if (!itr.hasNext() || !t.equals(itr.next()))
								return true;
						}

						if (itr.hasNext())
							return true;

						if ( !mt.returnType.equals(pmt.returnType) )
							return true;
					}
				}

				parent = parent.parent;
			}			
		}

		return false;
	}

	@Override
	public Boolean visit(Goal n) {
		if (n.f0.accept(this) == null)
			return null;

		if (n.f1.accept(this) == null)
			return null;

		// Check if all classes are initialized
		for (ClassType ct : classes.values()) {
			if ( !ct.found ) {
				return null;
			}
		}

		if (hasCycles())
			return null;

		if (hasOverload())
			return null;

		return true;
	}

	@Override
	public Boolean visit(MainClass n) {

		// Add class to table
		ClassType ct = addClass(n.f1.f0.toString());

		if (ct == null)
			return null;

		// Begin class scope
		scopeClass = ct;

		MethodType mt = addMethod("main", XType.OTHER);

		if (mt == null)
			return null;

		// Begin method scope
		scopeMethod = mt;

		// Add params to table
		if (addParam(n.f11.f0.toString(), XType.OTHER) == null)
			return null;

		// VarDeclaration
		Boolean ret = n.f14.accept(this);

		// End method scope
		scopeMethod = null;

		// End class scope
		scopeClass = null;

		return ret;
	}

	@Override
	public Boolean visit(TypeDeclaration n) {
		return n.f0.accept(this);
	}

	@Override
	public Boolean visit(ClassDeclaration n) {
		// Add class to table
		ClassType ct = addClass(n.f1.f0.toString());

		if (ct == null)
			return null;

		// Begin class scope
		scopeClass = ct;

		// VarDeclaration
		if (n.f3.accept(this) == null)
			return null;

		// MethodDeclaration
		Boolean ret = n.f4.accept(this);

		// End class scope
		scopeClass = null;

		return ret;
	}

	@Override
	public Boolean visit(ClassExtendsDeclaration n) {
		// Add class to table
		ClassType ct = addClass(n.f1.f0.toString(), n.f3.f0.toString());
		
		if (ct == null)
			return null;

		// Begin class scope
		scopeClass = ct;

		// VarDeclaration
		if (n.f5.accept(this) == null)
			return null;

		// MethodDeclaration
		Boolean ret = n.f6.accept(this);

		// End class scope
		scopeClass = null;

		return ret;
	}

	@Override
	public Boolean visit(VarDeclaration n) {
		// Add var to table
		if (addVar(n.f1.f0.toString(), getType(n.f0)) == null)
			return null;

		return true;
	}

	@Override
	public Boolean visit(MethodDeclaration n) {
		// Add method to table
		MethodType mt = addMethod(n.f2.f0.toString(), getType(n.f1));
		
		if (mt == null)
			return null;

		// Begin method scope
		scopeMethod = mt;

      	// FormalParameterList
      	if (n.f4.accept(this) == null)
      		return null;

      	// VarDeclaration
      	Boolean ret = n.f7.accept(this);

      	// End method scope
      	scopeMethod = null;

      	return ret;
	}

	@Override
	public Boolean visit(FormalParameterList n) {
		// FormalParameter
		if (n.f0.accept(this) == null)
			return null;

		// FormalParameterRest
		return n.f1.accept(this);
	}

	@Override
	public Boolean visit(FormalParameter n) {
		// Add param to table
		if (addParam(n.f1.f0.toString(), getType(n.f0)) == null)
			return null;

		return true;
	}

	@Override
	public Boolean visit(FormalParameterRest n) {
		// FormalParameter
		return n.f1.accept(this);
	}
}




















