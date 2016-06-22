package xxx;

import java.util.*;

public class ClassType {

	public HashMap<String, XType> vars;
	public HashMap<String, MethodType> methods;
	
	public String name;
	public boolean found;
	public ClassType parent;

	public ClassType(String n) {
		name = n;
		found = true;
		parent = null;

		vars = new HashMap<String, XType>();
		methods = new HashMap<String, MethodType>();
	}
}