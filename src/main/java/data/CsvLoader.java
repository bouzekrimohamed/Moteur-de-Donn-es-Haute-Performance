package data;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

public class CsvLoader {

    /**
     * Lit un fichier CSV et retourne une Table peuplée.
     * Se comporte comme ParquetReader.load().
     */
    public static Table load(String filePath) throws Exception {
        // 1. Extraire le nom de la table depuis le fichier
        String tableName = extractTableName(filePath);
        Table table = new Table(tableName);

        // 2. Configurer le format CSV (détection automatique des headers)
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath));
             CSVParser csvParser = new CSVParser(reader, format)) {

            // 3. Créer les colonnes dans la table à partir du header du CSV
            List<String> headerNames = csvParser.getHeaderNames();
            for (String header : headerNames) {
                table.addColumn(header);
            }

            List<Column> columns = table.getColumns();

            // 4. Parcourir les lignes et remplir la table
            for (CSVRecord record : csvParser) {
                // On vérifie que la ligne est complète
                if (record.size() == columns.size()) {
                    for (int i = 0; i < columns.size(); i++) {
                        String value = record.get(i);
                        // On ajoute la valeur brute (String)
                        // Note : On pourrait ajouter une détection de type ici plus tard
                        columns.get(i).add(value);
                    }
                }
            }
        }
        return table;
    }

    /** Extrait le nom du fichier sans l'extension */
    private static String extractTableName(String filePath) {
        String fileName = new File(filePath).getName();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}