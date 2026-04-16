package data;

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

public class ParquetReader {

    public static Table load(String filePath) throws IOException {
        Configuration conf = new Configuration();
        Path path = new Path(filePath);

        try (ParquetFileReader reader = ParquetFileReader.open(HadoopInputFile.fromPath(path, conf))) {
            MessageType schema = reader.getFooter().getFileMetaData().getSchema();

            String tableName = extractTableName(filePath);
            Table table = new Table(tableName);

            for (Type field : schema.getFields()) {
                table.addColumn(field.getName());
            }

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

    private static void readRow(Group group, MessageType schema, Table table) {
        for (Type field : schema.getFields()) {
            String columnName = field.getName();
            Object value = extractValue(group, field);
            table.addToColumn(columnName, value);
        }
    }

    private static Object extractValue(Group group, Type field) {
        String name = field.getName();
        int fieldIndex = group.getType().getFieldIndex(name);
        if (group.getFieldRepetitionCount(fieldIndex) == 0) {
            return null;
        }

        return switch (field.asPrimitiveType().getPrimitiveTypeName()) {
            case INT32 -> group.getInteger(name, 0);
            case INT64 -> group.getLong(name, 0);
            case FLOAT -> group.getFloat(name, 0);
            case DOUBLE -> group.getDouble(name, 0);
            case BOOLEAN -> group.getBoolean(name, 0);
            case BINARY, FIXED_LEN_BYTE_ARRAY -> group.getString(name, 0);
            default -> group.getValueToString(fieldIndex, 0);
        };
    }

    private static String extractTableName(String filePath) {
        String fileName = new java.io.File(filePath).getName();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
