package data;

import java.util.ArrayList;
import java.util.List;

public class Column {
	private final String name;
	private final List<Object> data;

	public Column (String s, List<Object> l){
		name=s;
		data=l;
	}

	public Column(String s){
		this(s, new ArrayList<Object>());
	}

	public boolean add(Object elem){
		return data.add(elem);
	}

	public boolean remove(Object o){ return data.remove(o);}

	public int size(){
		return data.size();
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

	@Override
    public Column clone(){
		List<Object> clone=new ArrayList<>();
        clone.addAll(data);
		return new Column(name, clone);
	}
}
