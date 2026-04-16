package data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private final String name;
    private final List<Table> base;

    public Database (String name){
        this.name=name;
        base= new ArrayList<>();
    }

    public boolean newTable(String s){
        return base.add(new Table(s));
    }

    public Table getTable(String s){
        for(Table t : base){
            if(s.equals(t.getName())){
                return t;
            }
        }
        return null;
    }

    public String createFromParquet(String filePath) throws IOException {
        Table table = ParquetReader.load("C:/Users/abel/Documents/Sorbonne/L3/S6/yellow_tripdata_2025-01.parquet");
        base.add(table);
        return table.getName();
    }
}
