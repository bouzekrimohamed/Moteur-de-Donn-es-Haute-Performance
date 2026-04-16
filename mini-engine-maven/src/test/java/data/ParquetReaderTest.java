package data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParquetReaderTest {
    private static final String PARQUET_FILE =
            "C:/projet base de donnee/Moteur-de-Donn-es-Haute-Performance/yellow_tripdata_2025-01.parquet";

    @Test
    void shouldLoadParquetFileIntoTable() throws Exception {
        Table table = ParquetReader.load(PARQUET_FILE);

        assertNotNull(table);
        assertEquals("yellow_tripdata_2025-01", table.getName());
        assertFalse(table.getColumns().isEmpty());
        assertTrue(table.getColumns().get(0).size() > 0);
    }
}
