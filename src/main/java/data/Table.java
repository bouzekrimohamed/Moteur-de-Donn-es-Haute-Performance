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

	public boolean addColumn(String name){
		return columns.add(new Column(name));
	}

	public boolean deleteColumn(String name){
		for(Column c : columns){
			if(c.getName()==name){
				return columns.remove(c);
			}
		}
	}

	public String getName() {
		return name;
	}
}
