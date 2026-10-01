package io.nop.duckdb;

import io.nop.tablesaw.xlsx.XlsxReadOptions;
import io.nop.tablesaw.xlsx.XlsxReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 file data plane: CSV/Parquet ingest and export, NULL/type semantics, contracts
 * (lifecycle / row counts / conflicts / overwrite / path escaping), XLSX bridge, roundtrips.
 */
public class TestDuckDbFiles {

    private final DuckDbEngine engine = new DuckDbEngine();
    private Connection conn;

    private Connection mem() throws Exception {
        if (conn == null) {
            conn = engine.openMemory();
        }
        return conn;
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
            conn = null;
        }
        engine.close();
    }

    private Path writeFixture(String name, String content) throws Exception {
        Path p = Files.createTempDirectory("duckdb-wi2").resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private List<Object[]> readRows(String sql) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        try (Statement st = mem().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Object[] row = new Object[cols];
                for (int i = 0; i < cols; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    @Test
    @Timeout(60)
    public void testReadCsvRowCountAndValues() throws Exception {
        Path csv = writeFixture("in.csv", "id,name,amount\n1,alpha,1.5\n2,beta,\n3,gamma,3.0\n");
        long n = DuckDbFiles.readCsv(mem(), csv.toString(), "t_in");
        assertEquals(3, n);
        List<Object[]> rows = readRows("SELECT id, name, amount FROM t_in ORDER BY id");
        assertEquals(3, rows.size());
        assertEquals("alpha", rows.get(0)[1]);
        assertNull(rows.get(1)[2], "empty CSV field must read as NULL");
    }

    @Test
    @Timeout(60)
    public void testParquetIngestAndExport() throws Exception {
        Path csv = writeFixture("src.csv", "id,val\n1,10\n2,20\n");
        Path parquet = Files.createTempDirectory("duckdb-wi2-pq").resolve("out.parquet");
        assertEquals(2, DuckDbFiles.writeParquet(mem(), "SELECT * FROM read_csv_auto('" + csv + "', header=true)", parquet.toString()));

        long n = DuckDbFiles.readParquet(mem(), parquet.toString(), "t_pq");
        assertEquals(2, n);
        List<Object[]> rows = readRows("SELECT sum(val) FROM t_pq");
        assertEquals(30L, ((Number) rows.get(0)[0]).longValue());
    }

    @Test
    @Timeout(60)
    public void testWriteCsvOverwriteContract() throws Exception {
        Path csv = writeFixture("src.csv", "k\n1\n2\n3\n");
        DuckDbFiles.readCsv(mem(), csv.toString(), "t_src");
        Path out = Files.createTempDirectory("duckdb-wi2-csv").resolve("out.csv");

        assertEquals(3, DuckDbFiles.writeCsv(mem(), "SELECT * FROM t_src", out.toString()));
        // documented overwrite semantics: second export silently replaces the file
        assertEquals(3, DuckDbFiles.writeCsv(mem(), "SELECT * FROM t_src", out.toString()));
        assertTrue(Files.size(out) > 0);
    }

    @Test
    @Timeout(60)
    public void testTableExistsFailsFast() throws Exception {
        Path csv = writeFixture("dup.csv", "k\n1\n");
        DuckDbFiles.readCsv(mem(), csv.toString(), "t_dup");
        NopDuckDbException e = assertThrows(NopDuckDbException.class,
                () -> DuckDbFiles.readCsv(mem(), csv.toString(), "t_dup"));
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_TABLE_EXISTS.getErrorCode(), e.getErrorCode());
    }

    @Test
    @Timeout(60)
    public void testFileNotFound() {
        NopDuckDbException e = assertThrows(NopDuckDbException.class,
                () -> DuckDbFiles.readCsv(mem(), "/no/such/file_wi2.csv", "t_missing"));
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_FILE_NOT_FOUND.getErrorCode(), e.getErrorCode());
    }

    @Test
    @Timeout(60)
    public void testNullSemanticsEndToEnd() throws Exception {
        // NULL exported as bare empty field, empty string as "" ; on re-read "" collapses to NULL
        Path csv = writeFixture("nulls.csv", "id,s\n1,\n2,\"\"\n3,x\n");
        long n = DuckDbFiles.readCsv(mem(), csv.toString(), "t_null");
        assertEquals(3, n);

        List<Object[]> rows = readRows("SELECT count(*), count(s), sum(id) FROM t_null");
        assertEquals(6L, ((Number) rows.get(0)[2]).longValue(), "sum over all rows");
        // empty string collapses to NULL on ingest, so only 'x' remains non-null
        assertEquals(1L, ((Number) rows.get(0)[1]).longValue(), "count(s) must ignore NULL");

        Path out = Files.createTempDirectory("duckdb-wi2-null").resolve("nulls_out.csv");
        assertEquals(3, DuckDbFiles.writeCsv(mem(), "SELECT * FROM t_null", out.toString()));
        String exported = Files.readString(out);
        assertTrue(exported.contains("3,x"), "plain row exported verbatim");
        // NULL (bare empty) and the collapsed "" are both bare empty fields on export
        assertTrue(exported.contains("1,\n") && exported.contains("2,\n"),
                "NULL exports as bare empty field, got: " + exported);
    }

    @Test
    @Timeout(60)
    public void testTypeInferenceAndParquetPreservation() throws Exception {
        Path csv = writeFixture("types.csv", "i,d,s\n1,1.5,alpha\n2,2.5,beta\n");
        DuckDbFiles.readCsv(mem(), csv.toString(), "t_types");

        List<Object[]> types = readRows("SELECT column_name, data_type FROM information_schema.columns "
                + "WHERE table_name='t_types' ORDER BY ordinal_position");
        assertEquals("BIGINT", types.get(0)[1]);
        assertEquals("DOUBLE", types.get(1)[1]);
        assertEquals("VARCHAR", types.get(2)[1]);

        Path parquet = Files.createTempDirectory("duckdb-wi2-types").resolve("types.parquet");
        DuckDbFiles.writeParquet(mem(), "SELECT * FROM t_types", parquet.toString());
        DuckDbFiles.readParquet(mem(), parquet.toString(), "t_types_pq");
        List<Object[]> types2 = readRows("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name='t_types_pq' ORDER BY ordinal_position");
        assertEquals(types.get(0)[1], types2.get(0)[0]);
        assertEquals(types.get(1)[1], types2.get(1)[0]);
        assertEquals(types.get(2)[1], types2.get(2)[0], "parquet roundtrip must preserve column types");
    }

    @Test
    @Timeout(60)
    public void testCsvToParquetToCsvRoundtripDataEquivalence() throws Exception {
        Path csv = writeFixture("rt.csv", "id,s,val\n1,alpha,1.5\n2,,\n3,gamma,\n");
        DuckDbFiles.readCsv(mem(), csv.toString(), "t_rt");
        Path parquet = Files.createTempDirectory("duckdb-wi2-rt").resolve("rt.parquet");
        DuckDbFiles.writeParquet(mem(), "SELECT * FROM t_rt ORDER BY id", parquet.toString());
        DuckDbFiles.readParquet(mem(), parquet.toString(), "t_rt2");
        // single parquet-in parquet-out chain
        Path parquet2 = Files.createTempDirectory("duckdb-wi2-rt3").resolve("rt2.parquet");
        assertEquals(3, DuckDbFiles.writeParquet(mem(), "SELECT * FROM t_rt2 ORDER BY id", parquet2.toString()));
        DuckDbFiles.readParquet(mem(), parquet2.toString(), "t_rt2b");
        List<Object[]> pqA = readRows("SELECT id, s, val FROM t_rt2 ORDER BY id");
        List<Object[]> pqB = readRows("SELECT id, s, val FROM t_rt2b ORDER BY id");
        assertEquals(pqA.size(), pqB.size());
        for (int i = 0; i < pqA.size(); i++) {
            for (int j = 0; j < pqA.get(i).length; j++) {
                assertEquals(pqA.get(i)[j], pqB.get(i)[j],
                        "parquet->duck->parquet row " + i + " col " + j + " must be data-equivalent");
            }
        }
        Path csvOut = Files.createTempDirectory("duckdb-wi2-rt2").resolve("rt_out.csv");
        assertEquals(3, DuckDbFiles.writeCsv(mem(), "SELECT * FROM t_rt2 ORDER BY id", csvOut.toString()));

        DuckDbFiles.readCsv(mem(), csvOut.toString(), "t_rt3");
        List<Object[]> original = readRows("SELECT id, s, val FROM t_rt ORDER BY id");
        List<Object[]> roundtripped = readRows("SELECT id, s, val FROM t_rt3 ORDER BY id");
        assertEquals(original.size(), roundtripped.size());
        for (int i = 0; i < original.size(); i++) {
            for (int j = 0; j < original.get(i).length; j++) {
                Object a = original.get(i)[j];
                Object b = roundtripped.get(i)[j];
                if (a instanceof Number && b instanceof Number) {
                    assertEquals(((Number) a).doubleValue(), ((Number) b).doubleValue(), 1e-9);
                } else {
                    assertEquals(a, b, "row " + i + " col " + j + " must survive csv->parquet->csv");
                }
            }
        }
    }

    @Test
    @Timeout(60)
    public void testQuotedPathEscapingAndInjectionNeutralized() throws Exception {
        Path dir = Files.createTempDirectory("duckdb-wi2'quote");
        Path csv = dir.resolve("it's.csv");
        Files.writeString(csv, "k\n7\n", StandardCharsets.UTF_8);
        assertEquals(1, DuckDbFiles.readCsv(mem(), csv.toString(), "t_quote"));
        List<Object[]> rows = readRows("SELECT k FROM t_quote");
        assertEquals(7, ((Number) rows.get(0)[0]).intValue());

        // injection attempt through the file path: escaping neutralizes the quote, so the evil
        // name is treated as a literal path. With no such file it fails fast (file-not-found)
        // before reaching SQL; with an existing file it reads that literal path without side effects.
        String evil = dir.resolve("x') AS t_evil(k INTEGER);--.csv").toString();
        NopDuckDbException missing = assertThrows(NopDuckDbException.class,
                () -> DuckDbFiles.readCsv(mem(), evil, "t_victim"));
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_FILE_NOT_FOUND.getErrorCode(), missing.getErrorCode());
        Files.writeString(java.nio.file.Path.of(evil), "k\n9\n", StandardCharsets.UTF_8);
        assertEquals(1, DuckDbFiles.readCsv(mem(), evil, "t_victim"),
                "escaped quoted path must read the literal file when it exists");
        assertFalse(tableExistsRaw("t_evil"), "injected table must not be created");
    }

    @Test
    @Timeout(60)
    public void testIoFailedOnCorruptParquet() throws Exception {
        // SQL-layer failure (file exists but is not Parquet) must surface as io-failed
        Path fake = writeFixture("fake.parquet", "this is not a parquet file\n");
        NopDuckDbException e = assertThrows(NopDuckDbException.class,
                () -> DuckDbFiles.readParquet(mem(), fake.toString(), "t_bad"));
        assertEquals(NopDuckDbErrors.ERR_DUCKDB_IO_FAILED.getErrorCode(), e.getErrorCode());
        assertNotNull(e.getCause(), "underlying SQLException must be preserved as cause");
    }

    private boolean tableExistsRaw(String name) throws Exception {
        try (Statement st = mem().createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_name='" + name + "'")) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    @Test
    @Timeout(60)
    public void testTableLifecycleContract() throws Exception {
        Path csv = writeFixture("life.csv", "k\n5\n");
        Connection a = engine.openMemory();
        assertEquals(1, DuckDbFiles.readCsv(a, csv.toString(), "t_life"));

        // visible on the same connection
        try (Statement st = a.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM t_life")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        a.close();

        // memory connections are isolated private databases: new connection must not see the table
        Connection b = engine.openMemory();
        assertFalse(tableExistsOn(b, "t_life"), "in-memory tables must not leak across connections");
        b.close();

        // file-backed connections persist ingested tables across close/reopen
        Path dbFile = Files.createTempDirectory("duckdb-wi2-life").resolve("life.duckdb");
        DuckDbEngine fileEngine = new DuckDbEngine();
        try {
            assertEquals(1, DuckDbFiles.readCsv(fileEngine.openFile(dbFile.toString()), csv.toString(), "t_life"));
        } finally {
            fileEngine.close();
        }
        DuckDbEngine reopen = new DuckDbEngine();
        try {
            try (Statement st = reopen.openFile(dbFile.toString()).createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM t_life")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "ingested table must persist in the .duckdb file");
            }
        } finally {
            reopen.close();
        }
    }

    private boolean tableExistsOn(Connection c, String name) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_name='" + name + "'")) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    @Test
    @Timeout(60)
    public void testBridgeReInferenceSemantics() throws Exception {
        // bridge behavior verified empirically on duckdb 1.5.6: zero-padded numeric strings stay
        // VARCHAR (sniffer preserves leading zeros); plain numeric strings are re-typed BIGINT.
        // xlsx string cells therefore survive only when zero-padded or alphanumeric.
        Path csv = writeFixture("drift.csv", "padded,plain\n00123,42\n00456,58\n");
        DuckDbFiles.readCsv(mem(), csv.toString(), "t_drift");
        List<Object[]> types = readRows("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name='t_drift' ORDER BY ordinal_position");
        assertEquals("VARCHAR", types.get(0)[0], "zero-padded string must stay VARCHAR");
        assertEquals("BIGINT", types.get(1)[0], "plain numeric string is re-typed BIGINT (documented drift)");
    }

    @Test
    @Timeout(60)
    public void testXlsxBridge() throws Exception {
        File xlsx = resourceFile("data/columns.xlsx");
        // data-driven expectation: read the same file with the underlying tablesaw reader
        tech.tablesaw.api.Table expected = new XlsxReader().read(XlsxReadOptions.builder(xlsx.getAbsolutePath()).build());
        long n = DuckDbFiles.readXlsx(mem(), xlsx.getAbsolutePath(), "t_xlsx");
        assertEquals(expected.rowCount(), n);
        List<Object[]> cols = readRows("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name='t_xlsx' ORDER BY ordinal_position");
        assertEquals(expected.columnNames().size(), cols.size());
        for (int i = 0; i < expected.columnNames().size(); i++) {
            assertEquals(expected.columnNames().get(i), cols.get(i)[0]);
        }
    }

    @Test
    @Timeout(60)
    public void testXlsxWithMissingValues() throws Exception {
        File xlsx = resourceFile("data/columns-with-missing-values.xlsx");
        tech.tablesaw.api.Table expected = new XlsxReader().read(XlsxReadOptions.builder(xlsx.getAbsolutePath()).build());
        assertEquals(expected.rowCount(), DuckDbFiles.readXlsx(mem(), xlsx.getAbsolutePath(), "t_xlsx_missing"));
    }

    @Test
    @Timeout(60)
    public void testE2eXlsxToParquet() throws Exception {
        File xlsx = resourceFile("data/columns.xlsx");
        Path parquet = Files.createTempDirectory("duckdb-wi2-e2e").resolve("e2e.parquet");
        DuckDbFiles.readXlsx(mem(), xlsx.getAbsolutePath(), "t_e2e");
        long n = DuckDbFiles.writeParquet(mem(), "SELECT * FROM t_e2e", parquet.toString());
        assertTrue(n > 0);

        long n2 = DuckDbFiles.readParquet(mem(), parquet.toString(), "t_e2e_pq");
        assertEquals(n, n2, "xlsx -> duckdb -> parquet -> duckdb full chain must preserve row count");
    }

    private File resourceFile(String relative) throws Exception {
        URI uri = TestDuckDbFiles.class.getResource("/io/nop/duckdb/" + relative).toURI();
        return new File(uri);
    }
}
