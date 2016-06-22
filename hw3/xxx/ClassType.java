package xxx;

import java.util.*;

public class ClassType {

	public LinkedHashMap<String, XType> vars;
	public LinkedHashMap<String, MethodType> methods;
	
	public String name;
	public boolean found;
	public ClassType parent;

	public ClassType(String n) {
		name = n;
		found = true;
		parent = null;

		vars = new LinkedHashMap<String, XType>();
		methods = new LinkedHashMap<String, MethodType>();
	}
}