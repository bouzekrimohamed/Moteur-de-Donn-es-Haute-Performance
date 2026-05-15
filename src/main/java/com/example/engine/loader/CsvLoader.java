package com.example.engine.loader;

import com.example.engine.storage.Column;
import com.example.engine.storage.Table;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Charge un fichier CSV dans une Table déjà créée (schéma connu).
 *
 * Règles :
 *  - La première ligne est l'en-tête (noms de colonnes).
 *  - Les colonnes du CSV doivent correspondre exactement à celles de la Table.
 *  - Le séparateur est détecté automatiquement (virgule ou point-virgule).
 *  - Les valeurs sont converties selon le type déclaré de la colonne.
 *  - Une ligne malformée (mauvais nombre de champs) est ignorée avec un warning.
 */
public class CsvLoader {

    /**
     * Charge le fichier CSV dans la table fournie.
     *
     * @param table    table cible (schéma déjà défini)
     * @param filePath chemin absolu vers le fichier .csv
     * @return nombre de lignes effectivement chargées
     */
    public static int load(Table table, String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {

            // 1. Lire l'en-tête
            String headerLine = reader.readLine();
            if (headerLine == null) throw new IOException("Fichier CSV vide : " + filePath);

            char separator = detectSeparator(headerLine);
            String[] headers = splitLine(headerLine, separator);

            // 2. Vérifier que les colonnes CSV correspondent à la table
            List<Column> tableCols = table.getColumnsInternal();
            validateHeaders(headers, tableCols, filePath);

            // 3. Lire les lignes de données
            int loaded = 0;
            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;

                String[] fields = splitLine(line, separator);
                if (fields.length != headers.length) {
                    System.err.printf("[CsvLoader] Ligne %d ignorée : %d champs attendus, %d trouvés%n",
                            lineNumber, headers.length, fields.length);
                    continue;
                }

                // Insérer chaque valeur dans la bonne colonne
                for (int i = 0; i < headers.length; i++) {
                    String colName = headers[i];
                    String raw = fields[i].trim();
                    Column col = table.getColumn(colName);
                    Object value = parseValue(raw, col.getType());
                    col.add(value);
                }
                loaded++;
            }
            return loaded;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Détecte le séparateur en comptant virgules vs point-virgules dans l'en-tête */
    private static char detectSeparator(String headerLine) {
        long commas     = headerLine.chars().filter(c -> c == ',').count();
        long semicolons = headerLine.chars().filter(c -> c == ';').count();
        return semicolons > commas ? ';' : ',';
    }

    /** Découpe une ligne CSV en tenant compte des champs entre guillemets */
    private static String[] splitLine(String line, char sep) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == sep && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    /** Vérifie que tous les headers du CSV existent dans la table */
    private static void validateHeaders(String[] headers, List<Column> cols, String filePath) {
        List<String> colNames = cols.stream().map(Column::getName).toList();
        for (String h : headers) {
            String trimmed = h.trim();
            if (!colNames.contains(trimmed)) {
                throw new IllegalArgumentException(
                        "Colonne CSV '" + trimmed + "' absente du schéma de la table. " +
                        "Colonnes attendues : " + colNames + " — fichier : " + filePath);
            }
        }
    }

    /** Convertit une valeur brute (String) vers le type Java correspondant */
    private static Object parseValue(String raw, String type) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return switch (type) {
                case "INT"     -> Integer.parseInt(raw);
                case "LONG"    -> Long.parseLong(raw);
                case "DOUBLE"  -> Double.parseDouble(raw);
                case "BOOLEAN" -> Boolean.parseBoolean(raw);
                default        -> raw; // STRING
            };
        } catch (NumberFormatException e) {
            // Valeur non parseable → on garde null plutôt que planter
            return null;
        }
    }
}
