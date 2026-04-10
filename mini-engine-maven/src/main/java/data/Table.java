package data;

import java.util.ArrayList;
import java.util.List;

public class Table {
    private final String name;
    private final List<Column> columns;

    public Table(String name, List<Column> columns) {
        this.name = name;
        this.columns = columns;
    }

    public Table(String name) {
        this(name, new ArrayList<>());
    }

    public boolean addColumn(String name) {
        return columns.add(new Column(name));
    }

    public boolean addToColumn(String name, Object o) {
        for (Column c : columns) {
            if (c.getName().equals(name)) {
                return c.add(o);
            }
        }
        return false;
    }

    public boolean removeColumn(String name) {
        for (Column c : columns) {
            if (c.getName().equals(name)) {
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

    public List<Column> getColumns() {
        List<Column> clone = new ArrayList<>();
        for (Column c : columns) {
            clone.add(c.clone());
        }
        return clone;
    }
}
