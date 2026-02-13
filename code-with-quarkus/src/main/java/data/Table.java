package data;

import java.util.ArrayList;
import java.util.List;

public class Table {
	private String name;
	private List<Column> columns;

	public Table (String s, List<Column> l){
		name=s;
		columns=l;
	}

	public void insertRow(List<Object> values){
		if(values.size()>columns.size()){
			throw new IllegalArgumentException();
		}

		int index=columns.get(0).size();

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
		return null;
	}
}
