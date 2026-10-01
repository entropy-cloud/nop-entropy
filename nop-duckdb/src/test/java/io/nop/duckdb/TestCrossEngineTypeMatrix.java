package io.nop.duckdb;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 type matrix: every dataset column x every parity leg reads back the golden values
 * (plan 2287). Recorded engine-type differences (not defects, see plan): DuckDB AVG
 * returns DOUBLE while H2 returns DECIMAL; tablesaw 0.43.1 has no DECIMAL column type so
 * dec lands in a DoubleColumn (compared via BigDecimal.valueOf, exact on dyadic values).
 */
public class TestCrossEngineTypeMatrix extends BaseTestCase {

    static Connection legA;
    static Path csv;

    @BeforeAll
    public static void init() throws Exception {
        // leg B routing: named duckdb datasource on its own file + duckdb dialect mapping
        Path legBFile = Files.createTempDirectory("duckdb-wi5-b").resolve("legb.duckdb");
        setTestConfig("parity.duck.jdbc-url", "jdbc:duckdb:" + legBFile);
        setTestConfig("nop.dao.config.query-space-to-dialect", "duck=duckdb");
        setTestConfig("nop.orm.init-database-schema", true);
        CoreInitialization.initialize();

        csv = CrossEngineLegs.writeCsv("duckdb-wi5");
        Path legAFile = Files.createTempDirectory("duckdb-wi5-a").resolve("lega.duckdb");
        legA = CrossEngineLegs.openDuckFileLeg(legAFile, csv);
        CrossEngineLegs.ingestOrm("duckdb.ParityRow");
        CrossEngineLegs.ingestOrm("duckdb.H2ParityRow");
    }

    @AfterAll
    public static void destroy() throws Exception {
        if (legA != null) {
            legA.close();
        }
        CoreInitialization.destroy();
    }

    /** leg A: raw SELECT * ordered by id from the engine-opened duckdb file */
    @Test
    @Timeout(120)
    public void testLegADuckDbExecutionLayer() throws Exception {
        List<Map<String, Object>> rows = CrossEngineLegs.query(
                legA, "SELECT id, grp, dec, big, name, flag, d, ts FROM parity_a ORDER BY id");
        assertEquals(CrossEngineDataset.rowCount(), rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            // rename to canonical prop names for the shared golden comparison
            row.put("id", rows.get(i).get("id"));
            row.put("grp", rows.get(i).get("grp"));
            row.put("decVal", rows.get(i).get("dec"));
            row.put("big", rows.get(i).get("big"));
            row.put("name", rows.get(i).get("name"));
            row.put("flag", rows.get(i).get("flag"));
            row.put("d", rows.get(i).get("d"));
            row.put("ts", rows.get(i).get("ts"));
            CrossEngineLegs.assertRowEqualsGolden(row, i, "legA");
        }
    }

    /** leg B: EQL QueryBean on the querySpace=duck entity - SQL pushed to DuckDB */
    @Test
    @Timeout(120)
    public void testLegBEqlOnDuckDb() {
        QueryBean query = new QueryBean();
        query.setSourceName("duckdb.ParityRow");
        query.setFieldNames(List.of("id", "grp", "decVal", "big", "name", "flag", "d", "ts"));
        IOrmTemplate orm = BeanContainer.getBeanByType(IOrmTemplate.class);
        List<Map<String, Object>> rows = orm.findListByQuery(query);
        assertEquals(CrossEngineDataset.rowCount(), rows.size());
        // order by id for stable comparison
        rows.sort(java.util.Comparator.comparingInt(r -> ((Number) r.get("id")).intValue()));
        for (int i = 0; i < rows.size(); i++) {
            CrossEngineLegs.assertRowEqualsGolden(rows.get(i), i, "legB");
        }
    }

    /** leg C: EQL QueryBean on the default h2 datasource - SQL pushed to the RDB */
    @Test
    @Timeout(120)
    public void testLegCEqlOnH2() {
        QueryBean query = new QueryBean();
        query.setSourceName("duckdb.H2ParityRow");
        query.setFieldNames(List.of("id", "grp", "decVal", "big", "name", "flag", "d", "ts"));
        IOrmTemplate orm = BeanContainer.getBeanByType(IOrmTemplate.class);
        List<Map<String, Object>> rows = orm.findListByQuery(query);
        assertEquals(CrossEngineDataset.rowCount(), rows.size());
        rows.sort(java.util.Comparator.comparingInt(r -> ((Number) r.get("id")).intValue()));
        for (int i = 0; i < rows.size(); i++) {
            CrossEngineLegs.assertRowEqualsGolden(rows.get(i), i, "legC");
        }
    }

    /**
     * leg D: tablesaw reads the same CSV. Column types are the tablesaw sniffing result
     * (dec -> DoubleColumn, no DECIMAL column type in tablesaw 0.43.1); values are
     * compared exactly on the dyadic dataset.
     */
    @Test
    @Timeout(120)
    public void testLegDTablesaw() {
        tech.tablesaw.api.Table t = CrossEngineLegs.tablesawLeg(csv);
        assertEquals(CrossEngineDataset.rowCount(), t.rowCount());
        // recorded sniffing result: small-valued id lands in IntColumn (tablesaw sniffs by
        // value range), big stays LongColumn - type mapping difference, values must agree
        tech.tablesaw.columns.Column<?> idCol = t.column("id");
        for (int i = 0; i < t.rowCount(); i++) {
            Map<String, Object> golden = CrossEngineDataset.goldenRow(i);
            long idActual = idCol instanceof tech.tablesaw.api.LongColumn lc
                    ? lc.get(i)
                    : ((tech.tablesaw.api.IntColumn) idCol).get(i);
            assertEquals(((Number) golden.get("id")).longValue(), idActual, "legD id row " + i);
            assertEquals(golden.get("grp"), t.stringColumn("grp").get(i), "legD grp row " + i);
            BigDecimal goldenDec = (BigDecimal) golden.get("decVal");
            if (goldenDec == null) {
                assertTrue(t.doubleColumn("dec").isMissing(i), "legD dec row " + i + " must be missing");
            } else {
                assertEquals(0, BigDecimal.valueOf(t.doubleColumn("dec").get(i))
                        .compareTo(goldenDec), "legD dec row " + i);
            }
            assertEquals(((Number) golden.get("big")).longValue(), t.longColumn("big").get(i),
                    "legD big row " + i + " (must not be double-mangled)");
            Object goldenName = golden.get("name");
            if (goldenName == null) {
                assertTrue(t.stringColumn("name").isMissing(i), "legD name row " + i);
            } else {
                assertEquals(goldenName, t.stringColumn("name").get(i), "legD name row " + i);
            }
            Object goldenFlag = golden.get("flag");
            if (goldenFlag == null) {
                assertTrue(t.booleanColumn("flag").isMissing(i), "legD flag row " + i);
            } else {
                assertEquals(goldenFlag, t.booleanColumn("flag").get(i), "legD flag row " + i);
            }
            assertEquals(golden.get("d"), t.dateColumn("d").get(i), "legD d row " + i);
            assertEquals(golden.get("ts"), t.dateTimeColumn("ts").get(i), "legD ts row " + i);
        }
    }

    /**
     * EQL leg B type fidelity: BIGINT reads as Long, DECIMAL as BigDecimal, DATE as
     * LocalDate. TIMESTAMP surfaces as java.sql.Timestamp on the EQL read path (recorded
     * platform behavior, normalized to LocalDateTime in the golden comparison).
     */
    @Test
    @Timeout(120)
    public void testLegBTypeFidelity() {
        QueryBean query = new QueryBean();
        query.setSourceName("duckdb.ParityRow");
        query.setFieldNames(List.of("id", "decVal", "big", "d", "ts"));
        IOrmTemplate orm = BeanContainer.getBeanByType(IOrmTemplate.class);
        List<Map<String, Object>> rows = orm.findListByQuery(query);
        Map<String, Object> first = rows.get(0);
        assertNotNull(first.get("id"));
        assertEquals(Long.class, first.get("id").getClass(), "BIGINT must surface as Long");
        assertEquals(java.math.BigDecimal.class, first.get("decVal").getClass(),
                "DECIMAL must surface as BigDecimal");
        assertEquals(java.time.LocalDate.class, first.get("d").getClass(), "DATE must surface as LocalDate");
        assertEquals(java.sql.Timestamp.class, first.get("ts").getClass(),
                "TIMESTAMP surfaces as java.sql.Timestamp on the EQL read path (recorded)");
        assertNull(CrossEngineLegs.normalize(
                rows.stream().filter(r -> ((Number) r.get("id")).intValue() == 9)
                        .findFirst().orElseThrow().get("name")),
                "NULL name must stay NULL through the ORM path");
    }
}
