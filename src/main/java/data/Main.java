package data;

public class Main {
    public static void main(String[] args) throws Exception {
        Database db = new Database("Test");
        String tableName = db.createFromParquet("C:/Users/abel/Documents/Sorbonne/L3/S6/yellow_tripdata_2025-01.parquet");

        Table table = db.getTable(tableName);
        System.out.println("Table : " + table.getName());
        for (Column col : table.getColumns()) {
            System.out.println("    Colonne : " + col.getName() + ", Premier élément : "+ col.get(0));
        }
    }
}