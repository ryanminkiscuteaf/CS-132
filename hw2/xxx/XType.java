package xxx;

public class XType {

	public enum TYPE {
		ARRAY, BOOLEAN, INTEGER, ID, OTHER
	}

	public static XType ARRAY = new XType(TYPE.ARRAY);
	public static XType BOOLEAN = new XType(TYPE.BOOLEAN);
	public static XType INTEGER = new XType(TYPE.INTEGER);
	public static XType OTHER = new XType(TYPE.OTHER);

	public String name;
	public TYPE type;

	public XType (TYPE t) {
		type = t;
		name = "";
	}

	public XType (TYPE t, String n) {
		type = t;
		name = n;
	}

	@Override
	public boolean equals(Object o) {

		if (o == this)
			return true;

		if (!(o instanceof XType))
			return false;

		XType x = (XType)o;

		if (type == x.type) {
			if (type == TYPE.ID) {
				return name.equals(x.name);
			}

			return true;
		}

		return false;
	}

}