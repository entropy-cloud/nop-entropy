package io.nop.duckdb;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7 repeatable performance baseline (plan 2289): the same 1M-row dataset and the same
 * GROUP BY aggregation on three engines - DuckDB via the nop-duckdb execution layer,
 * tablesaw, and RDB pushdown (H2 via nop-dao). Correctness is asserted on every run from
 * deterministic golden values; timings are only recorded (no threshold assertions - the
 * roadmap fixes a baseline, not a race). Run: ./mvnw test -pl nop-duckdb
 * -Dtest=TestDuckDbPerfBaseline (upstream modules must be installed; with -am add
 * -Dsurefire.failIfNoSpecifiedTests=false).
 *
 * <p>Recorded engine facts (review probes, see plan 2289): H2 CSVREAD defaults to treating
 * the first row as a header and infers VARCHAR for untyped columns (typed CTAS required);
 * H2 caches query results (QUERY_CACHE_SIZE default 8) so it is disabled for honest
 * measurements; DuckDB sum(INT) is HUGEINT, H2 sum is BIGINT, tablesaw aggregates are
 * DOUBLE - counts/sums are compared by numeric value (exact below 2^53), avg by relative
 * tolerance; tablesaw mean differs from SQL avg in the last ulp and is not asserted.
 */
public class TestDuckDbPerfBaseline extends BaseTestCase {
    static final int ROWS = 1_000_000;
    static final int GROUPS = 1000;
    static final int K_MOD = 977;

    static Path csv;
    static long[] goldenSum;
    static double[] goldenAvg;

    @BeforeAll
    public static void init() throws Exception {
        setTestConfig("nop.duckdb.threads", "4");
        setTestConfig("nop.duckdb.memory-limit", "1GB");
        CoreInitialization.initialize();

        csv = Files.createTempDirectory("duckdb-wi7-perf").resolve("perf.csv");
        goldenSum = new long[GROUPS];
        goldenAvg = new double[GROUPS];
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            w.println("g,k");
            for (int i = 0; i < ROWS; i++) {
                int g = i % GROUPS;
                int k = i % K_MOD;
                goldenSum[g] += k;
                w.println(g + "," + k);
            }
        }
        for (int g = 0; g < GROUPS; g++) {
            goldenAvg[g] = goldenSum[g] / (double) (ROWS / GROUPS);
        }
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static void checkGroupRow(int g, long count, long sum, Double avg, String leg) {
        assertEquals(ROWS / GROUPS, count, leg + " count for group " + g);
        assertEquals(goldenSum[g], sum, leg + " sum for group " + g);
        if (avg != null) {
            double tolerance = Math.max(1e-9, Math.abs(goldenAvg[g]) * 1e-9);
            assertTrue(Math.abs(avg - goldenAvg[g]) <= tolerance,
                    leg + " avg for group " + g + ": expected " + goldenAvg[g] + " got " + avg);
        }
    }

    @Test
    @Timeout(120)
    public void testCrossEngineAggregationBaseline() throws Exception {
        // leg 1: DuckDB via the nop-duckdb execution layer (container bean carries the
        // @InjectValue config; a plain new DuckDbEngine() would silently run on defaults)
        DuckDbEngine duckEngine = (DuckDbEngine) BeanContainer.instance().getBean("nopDuckDbEngine");
        Path db = Files.createTempDirectory("duckdb-wi7-perf").resolve("perf.duckdb");
        long duckMin = Long.MAX_VALUE;
        try (Connection conn = duckEngine.openFile(db.toString());
             Statement cfg = conn.createStatement();
             ResultSet setting = cfg.executeQuery(
                     "SELECT current_setting('threads'), current_setting('memory_limit')")) {
            setting.next();
            String threads = setting.getString(1);
            String memoryLimit = setting.getString(2);
            assertEquals("4", threads.trim(), "threads config must be applied (read-back check)");
            System.out.println("[PERF-BASELINE] duckdb config: threads=" + threads
                    + " memory_limit=" + memoryLimit);
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE t_perf AS SELECT * FROM read_csv_auto('" + csv + "', header=true)");
            }
            for (int run = 1; run <= 4; run++) {
                long t0 = System.nanoTime();
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT g, count(*), sum(k), avg(k) FROM t_perf "
                             + "GROUP BY g ORDER BY g")) {
                    int groups = 0;
                    while (rs.next()) {
                        int g = rs.getInt(1);
                        Number count = (Number) rs.getObject(2);
                        Number sum = (Number) rs.getObject(3);
                        checkGroupRow(g, count.longValue(), sum.longValue(), rs.getDouble(4), "duckdb");
                        groups++;
                    }
                    assertEquals(GROUPS, groups, "duckdb group count");
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                duckMin = Math.min(duckMin, ms);
                System.out.println("[PERF-BASELINE] leg=duckdb rows=" + ROWS
                        + " config=threads4/mem1GB run" + run + "=" + ms + "ms");
            }
        }

        // leg 2: tablesaw (mean intentionally not asserted: last-ulp difference from SQL avg)
        tech.tablesaw.api.Table table = tech.tablesaw.api.Table.read().csv(csv.toFile());
        long tablesawMin = Long.MAX_VALUE;
        for (int run = 1; run <= 4; run++) {
            long t0 = System.nanoTime();
            tech.tablesaw.api.Table agg = table.summarize("k",
                    tech.tablesaw.aggregate.AggregateFunctions.count,
                    tech.tablesaw.aggregate.AggregateFunctions.sum).by("g");
            assertEquals(GROUPS, agg.rowCount(), "tablesaw group count");
            for (int r = 0; r < agg.rowCount(); r++) {
                int g = agg.intColumn(0).get(r);
                long count = agg.numberColumn(1).get(r).longValue();
                long sum = agg.numberColumn(2).get(r).longValue();
                checkGroupRow(g, count, sum, null, "tablesaw");
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            tablesawMin = Math.min(tablesawMin, ms);
            System.out.println("[PERF-BASELINE] leg=tablesaw rows=" + ROWS
                    + " config=default run" + run + "=" + ms + "ms");
        }

        // leg 3: RDB pushdown (H2 via the nop-dao pooled datasource; result cache disabled)
        DataSource h2 = (DataSource) BeanContainer.instance().getBean("nopDataSource");
        long h2Min = Long.MAX_VALUE;
        try (Connection conn = h2.getConnection()) {
            try (Statement st = conn.createStatement()) {
                // H2 2.4.240 has no SET QUERY_CACHE_SIZE SQL; each measure run varies the SQL
                // text (trailing comment) so the per-session query result cache cannot serve it
                st.execute("CREATE TABLE t_perf(g INT, k INT) AS SELECT * FROM CSVREAD('"
                        + csv + "')");
            }
            for (int run = 1; run <= 4; run++) {
                long t0 = System.nanoTime();
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT g, count(*), sum(k), avg(k) FROM t_perf "
                             + "GROUP BY g ORDER BY g /* run=" + run + " */")) {
                    int groups = 0;
                    while (rs.next()) {
                        int g = rs.getInt(1);
                        long count = rs.getLong(2);
                        long sum = rs.getLong(3);
                        checkGroupRow(g, count, sum, rs.getDouble(4), "h2");
                        groups++;
                    }
                    assertEquals(GROUPS, groups, "h2 group count");
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                h2Min = Math.min(h2Min, ms);
                System.out.println("[PERF-BASELINE] leg=h2-pushdown rows=" + ROWS
                        + " config=nocache(varysql) run" + run + "=" + ms + "ms");
            }
        }

        System.out.println("[PERF-BASELINE] summary rows=" + ROWS
                + " minMs: duckdb=" + duckMin + " tablesaw=" + tablesawMin + " h2=" + h2Min
                + " (recorded baseline only, no threshold)");
        assertTrue(duckMin > 0 && tablesawMin > 0 && h2Min > 0,
                "timings must be positive (sanity, not a threshold)");
    }
}
