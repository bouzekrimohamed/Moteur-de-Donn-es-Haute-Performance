package com.example.engine.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Column {
    private final String name;
    private final String type; // STRING, INT, LONG, DOUBLE, BOOLEAN
    private final List<Object> data;

    public Column(String name, String type) {
        this.name = name;
        this.type = type;
        this.data = new ArrayList<>();
    }

    public Column(String name, String type, List<Object> data) {
        this.name = name;
        this.type = type;
        this.data = new ArrayList<>(data);
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
            if (Objects.equals(x, o)) return i;
            i++;
        }
        return -1;
    }

    public List<Object> getAll() {
        return new ArrayList<>(data);
    }

    public String getName() { return name; }
    public String getType() { return type; }

    public Column clone() {
        return new Column(name, type, data);
    }

    public void clear() { data.clear(); }
}
