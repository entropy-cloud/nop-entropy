package io.nop.duckdb;

import io.nop.api.core.beans.query.GroupFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI5 aggregation and join parity (plan 2287). Golden invariants are hardcoded business
 * values: count(id)=10 (= count(*): id is never NULL), grouped avg ignoring NULL
 * (g0=0.625, g1=1.25, g2=1.5; global avg=1.0), NULL-key inner join drops the unmatched
 * reference row (8 rows), and DECIMAL+BIGINT promotion is exact (dyadic dataset).
 *
 * <p>Recorded adjudications: the EQL leg does not participate in join parity
 * (QueryBean.joins is dimFields in-memory alignment with NULL-to-empty normalization,
 * a different semantic domain - see plan Deferred) and its global avg is not expressible
 * (ungrouped aggregates fall back to a primary-key dim column and generate invalid SQL);
 * leg B aggregation uses the grouped form with the group field also listed in fields
 * (internal dim columns are not mapped back into result rows).
 */
public class TestCrossEngineAggJoin extends BaseTestCase {

    static Connection legA;
    static Path csv;

    @BeforeAll
    public static void init() throws Exception {
        Path legBFile = Files.createTempDirectory("duckdb-wi5-agg-b").resolve("legb.duckdb");
        setTestConfig("parity.duck.jdbc-url", "jdbc:duckdb:" + legBFile);
        setTestConfig("nop.dao.config.query-space-to-dialect", "duck=duckdb");
        setTestConfig("nop.orm.init-database-schema", true);
        CoreInitialization.initialize();

        csv = CrossEngineLegs.writeCsv("duckdb-wi5-agg");
        Path legAFile = Files.createTempDirectory("duckdb-wi5-agg-a").resolve("lega.duckdb");
        legA = CrossEngineLegs.openDuckFileLeg(legAFile, csv);
        CrossEngineLegs.ingestOrm("duckdb.ParityRow");
        CrossEngineLegs.ingestOrm("duckdb.H2ParityRow");
        ingestJoinRef();
    }

    /** join helper rows into both SQL legs; the join key is the nullable link_id column
     *  (a PK column would be NOT NULL), so the NULL-key row exercises inner-join exclusion */
    private static void ingestJoinRef() throws Exception {
        // by id: getBeanByType(DataSource) is ambiguous (named duck + default h2 pools both exist)
        DataSource h2 = (DataSource) BeanContainer.instance().getBean("nopDataSource");
        try (Connection conn = h2.getConnection();
             var ps = conn.prepareStatement("INSERT INTO parity_ref (id, link_id, ref_name) VALUES (?, ?, ?)")) {
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

    @AfterAll
    public static void destroy() throws Exception {
        if (legA != null) {
            legA.close();
        }
        CoreInitialization.destroy();
    }

    private static void assertAvgGroups(List<Map<String, Object>> rows, String leg) {
        Map<String, BigDecimal> golden = Map.of(
                "g0", CrossEngineDataset.AVG_DEC_G0,
                "g1", CrossEngineDataset.AVG_DEC_G1,
                "g2", CrossEngineDataset.AVG_DEC_G2);
        assertEquals(3, rows.size(), leg + " group count");
        for (Map<String, Object> row : rows) {
            String grp = String.valueOf(row.get("grp"));
            Object v = row.get("avgDec");
            BigDecimal actual = v == null ? null
                    : (v instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) v).doubleValue()));
            assertNotNullOn(leg, grp, actual);
            assertEquals(0, golden.get(grp).compareTo(actual),
                    leg + " avg(dec) for group " + grp + ": expected " + golden.get(grp) + " got " + actual);
        }
    }

    private static void assertNotNullOn(String leg, String grp, BigDecimal v) {
        if (v == null) {
            throw new AssertionError(leg + " avg(dec) for group " + grp + " must not be null");
        }
    }

    /** count invariant across all four legs */
    @Test
    @Timeout(120)
    public void testCountAllLegs() throws Exception {
        // leg A
        List<Map<String, Object>> rows = CrossEngineLegs.query(legA, "SELECT count(*) AS c FROM parity_a");
        assertEquals(CrossEngineDataset.COUNT_ALL, ((Number) rows.get(0).get("c")).longValue(), "legA count");

        // leg B (EQL: count(id), see plan adjudication)
        QueryBean query = new QueryBean();
        query.setSourceName("duckdb.ParityRow");
        query.setFields(List.of(QueryFieldBean.forField("id").count().alias("cnt")));
        IOrmTemplate orm = BeanContainer.getBeanByType(IOrmTemplate.class);
        List<Map<String, Object>> bRows = orm.findListByQuery(query);
        assertEquals(1, bRows.size(), "legB count rows");
        assertEquals(CrossEngineDataset.COUNT_ALL, ((Number) bRows.get(0).get("cnt")).longValue(),
                "legB count(id) (equivalent to count(*): id is never NULL)");

        // leg C
        QueryBean h2Query = new QueryBean();
        h2Query.setSourceName("duckdb.H2ParityRow");
        h2Query.setFields(List.of(QueryFieldBean.forField("id").count().alias("cnt")));
        List<Map<String, Object>> cRows = orm.findListByQuery(h2Query);
        assertEquals(CrossEngineDataset.COUNT_ALL, ((Number) cRows.get(0).get("cnt")).longValue(), "legC count");

        // leg D
        assertEquals(CrossEngineDataset.COUNT_ALL, CrossEngineLegs.tablesawLeg(csv).rowCount(), "legD count");
    }

    /** grouped avg(dec) ignoring NULL across all four legs (DuckDB AVG returns DOUBLE - recorded) */
    @Test
    @Timeout(120)
    public void testAvgIgnoreNullAllLegs() throws Exception {
        // leg A (grouped + global)
        List<Map<String, Object>> aRows = CrossEngineLegs.query(legA,
                "SELECT grp, avg(dec) AS avgDec FROM parity_a GROUP BY grp ORDER BY grp");
        assertAvgGroups(aRows, "legA");
        List<Map<String, Object>> aGlobal = CrossEngineLegs.query(legA, "SELECT avg(dec) AS avgDec FROM parity_a");
        assertEquals(0, CrossEngineDataset.AVG_DEC_GLOBAL.compareTo(
                        BigDecimal.valueOf(((Number) aGlobal.get(0).get("avgDec")).doubleValue())),
                "legA global avg");

        // leg B (grouped form; global avg not expressible - see class javadoc)
        QueryBean query = new QueryBean();
        query.setSourceName("duckdb.ParityRow");
        query.setFields(List.of(QueryFieldBean.forField("grp"),
                QueryFieldBean.forField("decVal").aggFunc("avg").alias("avgDec")));
        query.setGroupBy(List.of(GroupFieldBean.forField("grp")));
        IOrmTemplate orm = BeanContainer.getBeanByType(IOrmTemplate.class);
        List<Map<String, Object>> bRows = orm.findListByQuery(query);
        bRows.sort(Comparator.comparing(r -> String.valueOf(r.get("grp"))));
        assertAvgGroups(bRows, "legB");

        // leg C
        QueryBean h2Query = new QueryBean();
        h2Query.setSourceName("duckdb.H2ParityRow");
        h2Query.setFields(List.of(QueryFieldBean.forField("grp"),
                QueryFieldBean.forField("decVal").aggFunc("avg").alias("avgDec")));
        h2Query.setGroupBy(List.of(GroupFieldBean.forField("grp")));
        List<Map<String, Object>> cRows = orm.findListByQuery(h2Query);
        cRows.sort(Comparator.comparing(r -> String.valueOf(r.get("grp"))));
        assertAvgGroups(cRows, "legC");
        // leg C global avg via native SQL: an ungrouped QueryBean aggregate is the
        // invalid-shape form (primary-key dim fallback) - see class javadoc
        // by id: getBeanByType(DataSource) is ambiguous (named duck + default h2 pools both exist)
        DataSource h2 = (DataSource) BeanContainer.instance().getBean("nopDataSource");
        try (Connection conn = h2.getConnection();
             var st = conn.createStatement();
             var rs = st.executeQuery("SELECT avg(dec_val) FROM parity_row_h2")) {
            rs.next();
            assertEquals(0, CrossEngineDataset.AVG_DEC_GLOBAL.compareTo(rs.getBigDecimal(1)),
                    "legC global avg");
        }

        // leg D (tablesaw mean over the double column; missing values ignored - recorded)
        tech.tablesaw.api.Table t = CrossEngineLegs.tablesawLeg(csv);
        tech.tablesaw.api.Table dAvg = t.summarize("dec", tech.tablesaw.aggregate.AggregateFunctions.mean)
                .by("grp");
        List<Map<String, Object>> dRows = new ArrayList<>();
        for (int i = 0; i < dAvg.rowCount(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("grp", dAvg.stringColumn(0).get(i));
            row.put("avgDec", dAvg.numberColumn(1).get(i));
            dRows.add(row);
        }
        dRows.sort(Comparator.comparing(r -> String.valueOf(r.get("grp"))));
        assertAvgGroups(dRows, "legD");
    }

    /** NULL-key inner join: the unmatched reference row is dropped on legs A/C (native SQL) and leg D */
    @Test
    @Timeout(120)
    public void testNullKeyInnerJoinDropsUnmatched() throws Exception {
        // leg A
        List<Map<String, Object>> aRows = CrossEngineLegs.query(legA,
                "SELECT count(*) AS c FROM parity_a p JOIN parity_ref_a r ON p.id = r.link_id");
        assertEquals(CrossEngineDataset.JOIN_MATCHED_ROWS, ((Number) aRows.get(0).get("c")).longValue(),
                "legA inner join must drop the NULL-key reference row");

        // leg C (native SQL on the h2 pool; the EQL leg does not join, see class javadoc)
        // by id: getBeanByType(DataSource) is ambiguous (named duck + default h2 pools both exist)
        DataSource h2 = (DataSource) BeanContainer.instance().getBean("nopDataSource");
        try (Connection conn = h2.getConnection();
             var st = conn.createStatement();
             var rs = st.executeQuery(
                     "SELECT count(*) FROM parity_row_h2 p JOIN parity_ref r ON p.id = r.link_id")) {
            rs.next();
            assertEquals(CrossEngineDataset.JOIN_MATCHED_ROWS, rs.getLong(1),
                    "legC inner join must drop the NULL-key reference row");
        }

        // leg D (tablesaw join; recorded probe of NA-key behavior)
        tech.tablesaw.api.Table t = CrossEngineLegs.tablesawLeg(csv);
        tech.tablesaw.api.Table ref = tech.tablesaw.api.Table.read().csv(
                // tablesaw has no PK constraint: the ref join key can be a nullable "id" column directly
                new java.io.StringReader("id,ref_name\n1,ref1\n2,ref2\n3,ref3\n4,ref4\n5,ref5\n6,ref6\n7,ref7\n8,ref8\n,ref-null\n"));
        tech.tablesaw.api.Table joined = t.joinOn("id").inner(ref);
        assertEquals(CrossEngineDataset.JOIN_MATCHED_ROWS, joined.rowCount(),
                "legD inner join must drop the NULL-key reference row (tablesaw NA-key behavior recorded)");
    }

    /** implicit DECIMAL + BIGINT promotion is exact on legs A/C/D (leg B: type fidelity, see matrix test) */
    @Test
    @Timeout(120)
    public void testDecimalPromotion() throws Exception {
        // leg A
        List<Map<String, Object>> aRows = CrossEngineLegs.query(legA,
                "SELECT id, dec + id AS promoted FROM parity_a ORDER BY id");
        assertPromotion(aRows, "legA");

        // leg C
        // by id: getBeanByType(DataSource) is ambiguous (named duck + default h2 pools both exist)
        DataSource h2 = (DataSource) BeanContainer.instance().getBean("nopDataSource");
        List<Map<String, Object>> cRows = new ArrayList<>();
        try (Connection conn = h2.getConnection();
             var st = conn.createStatement();
             var rs = st.executeQuery("SELECT id, dec_val + id AS promoted FROM parity_row_h2 ORDER BY id")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong(1));
                row.put("promoted", rs.getBigDecimal(2));
                cRows.add(row);
            }
        }
        assertPromotion(cRows, "legC");

        // leg D (double + long on the dyadic dataset is exact)
        tech.tablesaw.api.Table t = CrossEngineLegs.tablesawLeg(csv);
        for (int i = 0; i < t.rowCount(); i++) {
            BigDecimal goldenDec = CrossEngineDataset.dec(i);
            if (goldenDec == null) {
                org.junit.jupiter.api.Assertions.assertTrue(t.doubleColumn("dec").isMissing(i),
                        "legD promoted row " + i + " must be NULL");
            } else {
                long id = ((Number) t.column("id").get(i)).longValue();
                double promoted = t.doubleColumn("dec").get(i) + id;
                assertEquals(0, goldenDec.add(BigDecimal.valueOf(id)).compareTo(BigDecimal.valueOf(promoted)),
                        "legD promoted row " + i);
            }
        }
    }

    private static void assertPromotion(List<Map<String, Object>> rows, String leg) {
        assertEquals(CrossEngineDataset.rowCount(), rows.size(), leg + " promotion row count");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            assertEquals(((long) (i + 1)), ((Number) row.get("id")).longValue(), leg + " promotion id row " + i);
            BigDecimal dec = CrossEngineDataset.dec(i);
            Object promoted = CrossEngineLegs.normalize(row.get("promoted"));
            if (dec == null) {
                org.junit.jupiter.api.Assertions.assertNull(promoted, leg + " promoted row " + i + " must be NULL");
            } else {
                BigDecimal expected = dec.add(BigDecimal.valueOf(i + 1L));
                org.junit.jupiter.api.Assertions.assertEquals(0, expected.compareTo((BigDecimal) promoted),
                        leg + " promoted value row " + i);
            }
        }
    }
}
