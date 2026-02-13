package data;

import java.util.ArrayList;
import java.util.List;

public class Table {
	private final String name;
	private List<Column> columns;

	public Table (String s, List<Column> l){
		name=s;
		columns=l;
	}

	public boolean setId(String name){
		for(Column c:columns){
			if(c.getName().equals(name)){
				if()
			}
		}
	}

	public void insertRow(List<Object> values){
		if(values.size()>columns.size()){
			throw new IllegalArgumentException();
		}

		int index=columns.getFirst().size();

		for(int i=0; i<columns.size(); i++){
			if(i<values.size()){
				columns.get(i).add(values.get(i));
			}
			else{
				columns.get(i).add(null);
			}
		}
	}

	public List<Object> getByRow (int index){
		List<Object> res= new ArrayList<>();
		for(int i=0; i< columns.size(); i++){
			res.add(columns.get(i).get(index));
		}

		return res;
	}

	public String getName() {
		return name;
	}

	public List<Object> getById (Object id){
		List<Object> l=new ArrayList<>();
		int row;
		for(Column c : columns){
			if(c.isId()){
				List<Object> data=c.clone();
				for(int i=0; i<data.size(); i++){
					if(data.get(i).equals(id)){
						row=i;
						break;
					}
				}
				break;
			}
		}
		return l;
	}
}
