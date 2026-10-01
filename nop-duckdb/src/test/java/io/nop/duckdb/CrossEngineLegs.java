package io.nop.duckdb;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the four parity legs on the shared {@link CrossEngineDataset} (plan 2287):
 * leg A = DuckDB via the nop-duckdb execution layer (engine file + native SQL), leg B =
 * DuckDB via the routed ORM/EQL path (querySpace=duck entity), leg C = RDB pushdown via
 * ORM/EQL on the default h2 datasource, leg D = tablesaw. Legs A and B use independent
 * .duckdb files (single-writer semantics, plan 2286).
 */
final class CrossEngineLegs {
    private CrossEngineLegs() {
    }

    static Path writeCsv(String prefix) throws Exception {
        Path csv = Files.createTempDirectory(prefix).resolve("parity.csv");
        Files.writeString(csv, CrossEngineDataset.csvText(), StandardCharsets.UTF_8);
        return csv;
    }

    /** leg A: materialize the dataset in a dedicated duckdb file with explicit types */
    static Connection openDuckFileLeg(Path dbFile, Path csv) throws SQLException {
        DuckDbEngine engine = new DuckDbEngine();
        Connection conn = engine.openFile(dbFile.toString());
        String csvPath = csv.toString().replace("'", "''");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE parity_a AS SELECT * FROM read_csv('" + csvPath
                    + "', header=true, types={'id': 'BIGINT', 'grp': 'VARCHAR', "
                    + "'dec': 'DECIMAL(12,4)', 'big': 'BIGINT', 'name': 'VARCHAR', "
                    + "'flag': 'BOOLEAN', 'd': 'DATE', 'ts': 'TIMESTAMP'})");
            st.execute("CREATE TABLE parity_ref_a (id BIGINT, link_id BIGINT, ref_name VARCHAR)");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO parity_ref_a VALUES (?, ?, ?)")) {
                for (int i = 1; i <= 8; i++) {
                    ps.setLong(1, i);
                    ps.setLong(2, i);
                    ps.setString(3, "ref" + i);
                    ps.addBatch();
                }
                ps.setLong(1, 9);
                ps.setNull(2, java.sql.Types.BIGINT);
                ps.setString(3, "ref-null");
                ps.addBatch();
                ps.executeBatch();
            }
        }
        return conn;
    }

    /** leg B / leg C: ingest the dataset rows through the ORM (same code, routed backends) */
    static void ingestOrm(String entityName) {
        IOrmTemplate orm = BeanContainer.getBeanByType(IOrmTemplate.class);
        orm.runInSession(() -> {
            for (int i = 0; i < CrossEngineDataset.rowCount(); i++) {
                IOrmEntity e = orm.newEntity(entityName);
                e.orm_propValueByName("id", (long) (i + 1));
                e.orm_propValueByName("grp", CrossEngineDataset.grp(i));
                e.orm_propValueByName("decVal", CrossEngineDataset.dec(i));
                e.orm_propValueByName("big", CrossEngineDataset.big(i));
                e.orm_propValueByName("name", CrossEngineDataset.name(i));
                e.orm_propValueByName("flag", CrossEngineDataset.flag(i));
                e.orm_propValueByName("d", CrossEngineDataset.date(i));
                e.orm_propValueByName("ts", CrossEngineDataset.ts(i));
                orm.save(e);
            }
            orm.flushSession();
        });
    }

    /** normalizes a JDBC/ORM value to the golden representation */
    static Object normalize(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd.stripTrailingZeros();
        }
        if (v instanceof Number && !(v instanceof java.math.BigInteger)) {
            return ((Number) v).longValue();
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (v instanceof java.time.LocalDate || v instanceof java.time.LocalDateTime
                || v instanceof String || v instanceof Boolean || v instanceof java.math.BigInteger) {
            return v;
        }
        throw new IllegalStateException("unexpected parity value type: " + v.getClass());
    }

    static void assertRowEqualsGolden(Map<String, Object> row, int i, String leg) {
        Map<String, Object> golden = CrossEngineDataset.goldenRow(i);
        for (Map.Entry<String, Object> en : golden.entrySet()) {
            Object actual = normalize(row.get(en.getKey()));
            Object expected = normalize(en.getValue());
            if (expected instanceof BigDecimal ebd && actual instanceof BigDecimal abd) {
                if (ebd.compareTo(abd) != 0) {
                    throw new AssertionError(leg + " row " + i + " column " + en.getKey()
                            + ": expected " + ebd + " got " + abd);
                }
            } else if (!java.util.Objects.equals(expected, actual)) {
                throw new AssertionError(leg + " row " + i + " column " + en.getKey()
                        + ": expected " + expected + " (" + typeName(expected) + ") got "
                        + actual + " (" + typeName(actual) + ")");
            }
        }
    }

    private static String typeName(Object v) {
        return v == null ? "null" : v.getClass().getSimpleName();
    }

    static List<Map<String, Object>> query(Connection conn, String sql) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            int n = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int c = 1; c <= n; c++) {
                    row.put(rs.getMetaData().getColumnLabel(c), rs.getObject(c));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /** leg D: tablesaw read of the same CSV */
    static tech.tablesaw.api.Table tablesawLeg(Path csv) {
        try {
            return tech.tablesaw.api.Table.read().csv(new StringReader(
                    Files.readString(csv, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("tablesaw leg failed", e);
        }
    }
}
