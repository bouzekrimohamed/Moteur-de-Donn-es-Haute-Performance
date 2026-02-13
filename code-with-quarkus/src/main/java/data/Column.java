package data;

import java.util.ArrayList;
import java.util.List;

public class Column {
	private String name;
	private boolean isId=false;
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

	public void toggleId(){
		isId=!isId;
	}

	public boolean isId(){
		return isId;
	}

	public Object get(int index){
		return data.get(index);
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
