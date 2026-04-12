package data;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;

import java.io.IOException;

public class ParquetReader {

    /**
     * Lit un fichier Parquet et retourne une Table peuplée.
     *
     * @param filePath chemin vers le fichier .parquet
     * @return une Table dont les colonnes correspondent au schéma Parquet
     */
    public static Table load(String filePath) throws IOException {

        Configuration conf = new Configuration();
        Path path = new Path(filePath);

        try (ParquetFileReader reader =
                     ParquetFileReader.open(HadoopInputFile.fromPath(path, conf))) {

            MessageType schema = reader.getFooter().getFileMetaData().getSchema();

            // 1. Créer la table à partir du nom du fichier
            String tableName = extractTableName(filePath);
            Table table = new Table(tableName);

            // 2. Créer une colonne par champ du schéma Parquet
            for (Type field : schema.getFields()) {
                table.addColumn(field.getName());
            }

            // 3. Lire les données par "row group" (blocs Parquet)
            PageReadStore pages;
            while ((pages = reader.readNextRowGroup()) != null) {

                long rowCount = pages.getRowCount();
                MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
                RecordReader<Group> recordReader =
                        columnIO.getRecordReader(pages, new GroupRecordConverter(schema));

                for (long i = 0; i < rowCount; i++) {
                    Group group = recordReader.read();
                    readRow(group, schema, table);
                }
            }

            return table;
        }
    }

    /**
     * Lit une ligne (Group) et insère chaque valeur dans la bonne colonne.
     */
    private static void readRow(Group group, MessageType schema, Table table) {
        for (Type field : schema.getFields()) {
            String columnName = field.getName();
            Object value = extractValue(group, field);
            table.addToColumn(columnName, value);
        }
    }

    /**
     * Extrait la valeur d'un champ en tenant compte de son type primitif.
     * Les valeurs nulles (champ absent dans le Group) sont gérées proprement.
     */
    private static Object extractValue(Group group, Type field) {
        String name = field.getName();

        // Un Group Parquet peut ne pas contenir de valeur pour un champ nullable
        int fieldIndex = group.getType().getFieldIndex(name);
        if (group.getFieldRepetitionCount(fieldIndex) == 0) {
            return null; // valeur nulle
        }

        // On caste selon le type primitif Parquet
        return switch (field.asPrimitiveType().getPrimitiveTypeName()) {
            case INT32   -> group.getInteger(name, 0);
            case INT64   -> group.getLong(name, 0);
            case FLOAT   -> group.getFloat(name, 0);
            case DOUBLE  -> group.getDouble(name, 0);
            case BOOLEAN -> group.getBoolean(name, 0);
            case BINARY,
                 FIXED_LEN_BYTE_ARRAY -> group.getString(name, 0); // UTF-8 en général
            default      -> group.getValueToString(fieldIndex, 0);
        };
    }

    /** Extrait "maTable" depuis "/chemin/vers/maTable.parquet" */
    private static String extractTableName(String filePath) {
        String fileName = new java.io.File(filePath).getName();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}