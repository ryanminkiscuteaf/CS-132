package xxx;

import java.util.*;

public class MethodType {

	public LinkedHashMap<String, XType> params;
	public LinkedHashMap<String, XType> vars;

	public String name;
	public XType returnType;

	public MethodType(String n, XType rt) {
		name = n;
		returnType = rt;

		params = new LinkedHashMap<String, XType>();
		vars = new LinkedHashMap<String, XType>();
	}
}