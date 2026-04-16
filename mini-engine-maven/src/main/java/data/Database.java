package data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private final String name;
    private final List<Table> base;

    public Database(String name) {
        this.name = name;
        this.base = new ArrayList<>();
    }

    public boolean newTable(String tableName) {
        return base.add(new Table(tableName));
    }

    public Table getTable(String tableName) {
        for (Table table : base) {
            if (tableName.equals(table.getName())) {
                return table;
            }
        }
        return null;
    }

    public String createFromParquet(String filePath) throws IOException {
        Table table = ParquetReader.load(filePath);
        base.add(table);
        return table.getName();
    }

    public String getName() {
        return name;
    }
}
