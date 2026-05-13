package com.example.engine.loader;

import com.example.engine.storage.Column;
import com.example.engine.storage.Table;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;

import java.io.IOException;
import java.util.List;

/**
 * Charge un fichier Parquet dans une Table.
 *
 * Deux modes :
 *  - load(filePath)         → crée automatiquement la Table depuis le schéma Parquet
 *  - load(table, filePath)  → charge dans une Table existante (schéma déjà défini)
 */
public class ParquetLoader {

    /**
     * Crée et peuple une Table à partir d'un fichier Parquet.
     * Le schéma (colonnes + types) est déduit automatiquement du fichier.
     *
     * @param filePath chemin absolu vers le fichier .parquet
     * @return Table peuplée
     */
    public static Table loadAsNewTable(String filePath) throws IOException {
        Configuration conf = new Configuration();
        Path path = new Path(filePath);

        try (ParquetFileReader reader =
                     ParquetFileReader.open(HadoopInputFile.fromPath(path, conf))) {

            MessageType schema = reader.getFooter().getFileMetaData().getSchema();
            String tableName = extractTableName(filePath);
            Table table = new Table(tableName);

            // Créer une colonne par champ Parquet, avec le type Java correspondant
            for (Type field : schema.getFields()) {
                String javaType = parquetTypeToJava(field);
                table.addColumn(field.getName(), javaType);
            }

            int loaded = readRowGroups(reader, schema, table);
            System.out.printf("[ParquetLoader] %d lignes chargées depuis %s%n", loaded, filePath);
            return table;
        }
    }

    /**
     * Charge un fichier Parquet dans une Table existante.
     * Les colonnes du fichier doivent correspondre à celles de la Table.
     *
     * @param table    table cible (schéma déjà défini)
     * @param filePath chemin absolu vers le fichier .parquet
     * @return nombre de lignes chargées
     */
    public static int load(Table table, String filePath) throws IOException {
        Configuration conf = new Configuration();
        Path path = new Path(filePath);

        try (ParquetFileReader reader =
                     ParquetFileReader.open(HadoopInputFile.fromPath(path, conf))) {

            MessageType schema = reader.getFooter().getFileMetaData().getSchema();
            validateSchema(schema, table, filePath);

            int loaded = readRowGroups(reader, schema, table);
            System.out.printf("[ParquetLoader] %d lignes chargées dans '%s' depuis %s%n",
                    loaded, table.getName(), filePath);
            return loaded;
        }
    }

    // -----------------------------------------------------------------------
    // Lecture des row groups (blocs Parquet)
    // -----------------------------------------------------------------------

    private static int readRowGroups(ParquetFileReader reader, MessageType schema, Table table)
            throws IOException {
        int total = 0;
        PageReadStore pages;

        while ((pages = reader.readNextRowGroup()) != null) {
            long rowCount = pages.getRowCount();
            MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
            RecordReader<Group> recordReader =
                    columnIO.getRecordReader(pages, new GroupRecordConverter(schema));

            for (long i = 0; i < rowCount; i++) {
                Group group = recordReader.read();
                readRow(group, schema, table);
                total++;
            }
        }
        return total;
    }

    /** Lit une ligne (Group Parquet) et insère chaque valeur dans la bonne colonne */
    private static void readRow(Group group, MessageType schema, Table table) {
        for (Type field : schema.getFields()) {
            String columnName = field.getName();
            Object value = extractValue(group, field);
            table.addToColumn(columnName, value);
        }
    }

    /**
     * Extrait la valeur d'un champ en tenant compte de son type primitif.
     * Les valeurs nulles (champ absent) sont retournées comme null.
     */
    private static Object extractValue(Group group, Type field) {
        String name = field.getName();
        int fieldIndex = group.getType().getFieldIndex(name);

        if (group.getFieldRepetitionCount(fieldIndex) == 0) {
            return null; // champ nullable absent
        }

        return switch (field.asPrimitiveType().getPrimitiveTypeName()) {
            case INT32                -> group.getInteger(name, 0);
            case INT64                -> group.getLong(name, 0);
            case FLOAT                -> group.getFloat(name, 0);
            case DOUBLE               -> group.getDouble(name, 0);
            case BOOLEAN              -> group.getBoolean(name, 0);
            case BINARY,
                 FIXED_LEN_BYTE_ARRAY -> group.getString(name, 0);
            default                   -> group.getValueToString(fieldIndex, 0);
        };
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Mappe un type Parquet primitif vers le type String de notre moteur */
    private static String parquetTypeToJava(Type field) {
        if (!field.isPrimitive()) return "STRING";
        return switch (field.asPrimitiveType().getPrimitiveTypeName()) {
            case INT32   -> "INT";
            case INT64   -> "LONG";
            case FLOAT,
                 DOUBLE  -> "DOUBLE";
            case BOOLEAN -> "BOOLEAN";
            default      -> "STRING";
        };
    }

    /** Vérifie que chaque colonne Parquet existe dans la table cible */
    private static void validateSchema(MessageType schema, Table table, String filePath) {
        List<Column> cols = table.getColumnsInternal();
        List<String> colNames = cols.stream().map(Column::getName).toList();
        for (Type field : schema.getFields()) {
            if (!colNames.contains(field.getName())) {
                throw new IllegalArgumentException(
                        "Colonne Parquet '" + field.getName() + "' absente du schéma de la table '" +
                        table.getName() + "'. Colonnes attendues : " + colNames);
            }
        }
    }

    /** Extrait "maTable" depuis "/chemin/vers/maTable.parquet" */
    private static String extractTableName(String filePath) {
        String fileName = new java.io.File(filePath).getName();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
