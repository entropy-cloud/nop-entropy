package io.nop.duckdb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WI5 cross-engine parity dataset (plan 2287): 10 rows x 8 columns, shared by all four legs.
 *
 * <p>Golden invariants are anchored here as typed constants - never as snapshot files.
 * Design constraints (plan adjudications):
 * <ul>
 * <li>dec values are dyadic rationals AND every aggregate group's sum/count quotient is
 * dyadic again (g0: 4 rows sum 2.5 avg 0.625; g1: 4 rows sum 5.0 avg 1.25; g2: one non-null
 * 1.5 + one NULL avg 1.5; global sum 9.0 avg 1.0), so DOUBLE (DuckDB AVG) and DECIMAL (H2)
 * agree exactly and BigDecimal comparisons are artifact-free.</li>
 * <li>big values straddle 2^53 so any double mangling is detectable.</li>
 * <li>NULL appears in dec (1x), name (1x), flag (2x); id (primary key) is never NULL, which
 * makes count(id) equivalent to count(*) on this dataset.</li>
 * <li>No leading-zero or empty-string values (WI2-documented bridge drift shapes excluded).</li>
 * </ul>
 */
public final class CrossEngineDataset {
    public static final BigDecimal D0_25 = new BigDecimal("0.25");
    public static final BigDecimal D0_50 = new BigDecimal("0.50");
    public static final BigDecimal D0_75 = new BigDecimal("0.75");
    public static final BigDecimal D1_00 = new BigDecimal("1.00");
    public static final BigDecimal D1_25 = new BigDecimal("1.25");
    public static final BigDecimal D1_50 = new BigDecimal("1.50");

    public static final BigDecimal SUM_DEC_GLOBAL = new BigDecimal("9.0");
    public static final BigDecimal AVG_DEC_GLOBAL = new BigDecimal("1.0");
    public static final BigDecimal AVG_DEC_G0 = new BigDecimal("0.625");
    public static final BigDecimal AVG_DEC_G1 = new BigDecimal("1.25");
    public static final BigDecimal AVG_DEC_G2 = new BigDecimal("1.5");
    public static final long COUNT_ALL = 10;
    public static final long COUNT_DEC_NON_NULL = 9;

    /** ref rows for the NULL-key inner join parity: ids 1..8 plus one NULL-key row */
    public static final long JOIN_MATCHED_ROWS = 8;

    private static final String[] GRP = {"g0", "g0", "g0", "g0", "g1", "g1", "g1", "g1", "g2", "g2"};
    private static final BigDecimal[] DEC = {
            D0_25, D0_50, D0_75, D1_00, D1_25, D1_50, D1_00, D1_25, D1_50, null};
    private static final long[] BIG = {
            9007199254740993L, 4503599627370496L, 9007199254740991L, 2251799813685248L,
            1125899906842624L, 562949953421312L, 281474976710656L, 140737488355328L,
            70368744177664L, 35184372088832L};
    private static final String[] NAME = {
            "uñíçode-λ", "plain", "a-b-c", "n4", "n5", "n6", "n7", "n8", null, "n10"};
    private static final Boolean[] FLAG = {true, false, true, null, false, true, null, false, true, true};
    private static final LocalDate[] D = {
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 1), LocalDate.of(2025, 12, 31),
            LocalDate.of(2024, 2, 29), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1),
            LocalDate.of(2027, 1, 1)};
    private static final LocalDateTime[] TS = {
            LocalDateTime.of(2026, 1, 15, 10, 30, 0),
            LocalDateTime.of(2026, 2, 1, 23, 59, 59),
            LocalDateTime.of(2025, 12, 31, 0, 0, 1),
            LocalDateTime.of(2024, 2, 29, 12, 0, 0),
            LocalDateTime.of(2026, 6, 30, 6, 15, 30),
            LocalDateTime.of(2026, 7, 1, 18, 45, 0),
            LocalDateTime.of(2026, 8, 15, 8, 8, 8),
            LocalDateTime.of(2026, 9, 30, 21, 0, 0),
            LocalDateTime.of(2026, 10, 1, 5, 5, 5),
            LocalDateTime.of(2027, 1, 1, 1, 1, 1)};

    private CrossEngineDataset() {
    }

    public static int rowCount() {
        return 10;
    }

    public static String grp(int i) {
        return GRP[i];
    }

    public static BigDecimal dec(int i) {
        return DEC[i];
    }

    public static long big(int i) {
        return BIG[i];
    }

    public static String name(int i) {
        return NAME[i];
    }

    public static Boolean flag(int i) {
        return FLAG[i];
    }

    public static LocalDate date(int i) {
        return D[i];
    }

    public static LocalDateTime ts(int i) {
        return TS[i];
    }

    /**
     * Deterministic CSV shared by leg A (DuckDB read_csv with explicit types) and leg D
     * (tablesaw). NULL is the empty field (WI2 contract: empty field parses as NULL).
     */
    public static String csvText() {
        StringBuilder sb = new StringBuilder("id,grp,dec,big,name,flag,d,ts\n");
        for (int i = 0; i < rowCount(); i++) {
            sb.append(i + 1).append(',')
                    .append(GRP[i]).append(',')
                    .append(DEC[i] == null ? "" : DEC[i].toPlainString()).append(',')
                    .append(BIG[i]).append(',')
                    .append(NAME[i] == null ? "" : NAME[i]).append(',')
                    .append(FLAG[i] == null ? "" : FLAG[i]).append(',')
                    .append(D[i]).append(',')
                    .append(TS[i].format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .append('\n');
        }
        return sb.toString();
    }

    /** typed row map for golden comparison, keyed by canonical names */
    public static Map<String, Object> goldenRow(int i) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", (long) (i + 1));
        row.put("grp", GRP[i]);
        row.put("decVal", DEC[i]);
        row.put("big", BIG[i]);
        row.put("name", NAME[i]);
        row.put("flag", FLAG[i]);
        row.put("d", D[i]);
        row.put("ts", TS[i]);
        return row;
    }

    public static List<Map<String, Object>> goldenRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount(); i++) {
            rows.add(goldenRow(i));
        }
        return rows;
    }
}
