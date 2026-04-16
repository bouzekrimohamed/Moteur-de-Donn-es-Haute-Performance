package data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Column {
    private final String name;
    private final List<Object> data;

    public Column(String name, List<Object> data) {
        this.name = name;
        this.data = data;
    }

    public Column(String name) {
        this(name, new ArrayList<>());
    }

    public boolean add(Object elem) {
        return data.add(elem);
    }

    public boolean remove(Object o) {
        return data.remove(o);
    }

    public int size() {
        return data.size();
    }

    public Object get(int index) {
        return data.get(index);
    }

    public int getIndex(Object o) {
        int i = 0;
        for (Object x : data) {
            if (Objects.equals(x, o)) {
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
    public Column clone() {
        List<Object> cloned = new ArrayList<>();
        cloned.addAll(data);
        return new Column(name, cloned);
    }
}
