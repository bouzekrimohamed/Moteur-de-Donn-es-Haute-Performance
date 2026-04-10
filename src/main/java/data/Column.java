package data;

import java.util.ArrayList;
import java.util.List;

public class Column {
	private final String name;
	private final List<Object> data;

	public Column (String s){
		name=s;
		data = new ArrayList<Object>();
	}


	public boolean add(Object elem){
		return data.add(elem);
	}


	public Object get(int index){
		return data.get(index);
	}

	public int getIndex (Object o){
		int i=0;
		for (Object x : data){
			if(x.equals(o)){
				return i;
			}
			i++;
		}
		return -1;
	}

	public String getName() {
		return name;
	}
}
