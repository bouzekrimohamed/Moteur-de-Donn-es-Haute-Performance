package data;

import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {

        // Charge la table depuis un fichier Parquet
        Table table = ParquetReader.load("C:/Users/abel/Documents/Sorbonne/L3/S6/yellow_tripdata_2025-01.parquet");

        List<Column> columns = table.getColumns();
        if (columns.isEmpty()) {
            System.out.println("La table est vide.");
            return;
        }

        // Calcul du nombre de lignes
        int rowCount = 0;
        for (Column c : columns) {
            int size = 0;
            // On compte via get() jusqu'à IndexOutOfBoundsException
            try { while (true) { c.get(size); size++; } }
            catch (IndexOutOfBoundsException ignored) {}
            rowCount = size;
            break;
        }

        System.out.println("=== Table : " + table.getName() + " ===");
        System.out.printf("  %d colonne(s), %d ligne(s)%n%n", columns.size(), rowCount);

        // Calcul de la largeur de chaque colonne (max entre le nom et les valeurs)
        int[] widths = new int[columns.size()];
        for (int col = 0; col < columns.size(); col++) {
            widths[col] = columns.get(col).getName().length();
            for (int row = 0; row < rowCount; row++) {
                Object val = columns.get(col).get(row);
                int len = (val == null ? "null" : val.toString()).length();
                if (len > widths[col]) widths[col] = len;
            }
        }

        // Ligne de séparation
        StringBuilder separator = new StringBuilder("+");
        for (int w : widths) separator.append("-".repeat(w + 2)).append("+");

        // En-tête
        System.out.println(separator);
        StringBuilder header = new StringBuilder("|");
        for (int col = 0; col < columns.size(); col++) {
            header.append(String.format(" %-" + widths[col] + "s |", columns.get(col).getName()));
        }
        System.out.println(header);
        System.out.println(separator);

        // Lignes de données
        for (int row = 0; row < rowCount; row++) {
            StringBuilder line = new StringBuilder("|");
            for (int col = 0; col < columns.size(); col++) {
                Object val = columns.get(col).get(row);
                line.append(String.format(" %-" + widths[col] + "s |", val == null ? "null" : val));
            }
            System.out.println(line);
        }
        System.out.println(separator);
    }
}