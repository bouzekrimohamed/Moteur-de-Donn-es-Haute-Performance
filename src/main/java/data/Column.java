package data;

import java.util.ArrayList;
import java.util.List;

public class Column {
	private String name;
	private List<Object> data= new ArrayList<>();

	public Column(String s){
		name=s;
	}

	public void add(Object elem){
		data.add(elem);
	}

	public int size(){
		return data.size();
	}

	public Object get(int index){
		return data.get(index);
	}

	public int getIndex (Object o){
		int i=0;
		for (Object x : data){
			if(x==o){
				return i;
			}
			i++;
		}
		return null;
	}

	public String getName() {
		return name;
	}

	public List<Object> clone(){
		List<Object> clone=new ArrayList<>();
        clone.addAll(data);
		return clone;
	}
}
