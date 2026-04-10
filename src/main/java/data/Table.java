package data;

import java.util.ArrayList;
import java.util.List;

public class Table {
	private final String name;
	private final List<Column> columns;

	public Table (String s, List<Column> l){
		name=s;
		columns=l;
	}

	public Table (String s){
		this(s, new ArrayList<Column>());
	}

	public boolean addColumn(String name){
		return columns.add(new Column(name));
	}

	public boolean addToColumn(String name, Object o){
		for(Column c:columns){
			if(c.getName().equals(name)){
				return c.add(o);
			}
		}
		return false;
	}

	public boolean removeColumn(String name){
		for(Column c : columns){
			if(c.getName().equals(name)){
				return columns.remove(c);
			}
		}
		return false;
	}

	public boolean removeFromColumn(String name, Object o) {
		for (Column c : columns) {
			if (c.getName().equals(name)) {
				return c.remove(o);
			}
		}
		return false;
	}

	public String getName() {
		return name;
	}

	public List<Column> getColumns(){
		List<Column> clone = new ArrayList<Column>();
		for(Column c : columns){
			clone.add(c.clone());
		}
		return clone;
	}
}
