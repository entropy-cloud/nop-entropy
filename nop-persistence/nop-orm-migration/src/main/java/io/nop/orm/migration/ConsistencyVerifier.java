/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.migration.TargetShardMapping.PhysicalTable;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.TreeMap;

import static io.nop.orm.migration.MigrationErrors.ARG_MISMATCH_COUNT;
import static io.nop.orm.migration.MigrationErrors.ARG_MISMATCH_DETAIL;
import static io.nop.orm.migration.MigrationErrors.ARG_SOURCE_COUNT;
import static io.nop.orm.migration.MigrationErrors.ARG_TABLE_NAME;
import static io.nop.orm.migration.MigrationErrors.ARG_TARGET_COUNT;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CHECKSUM_MISMATCH;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_COUNT_MISMATCH;

/**
 * 三级一致性校验（设计 §4.6 裁定 4 + M4）：count 必选 → 抽样 checksum（sample-ratio）→ 逐行
 * checksum（full-checksum）。
 *
 * <p><b>全部应用侧计算</b>：逐行按主键序双游标配对（扩容场景按逻辑表聚合：源 N 片之和 vs 目标
 * M 片之和），行值规范化串（null 专用标记、数值 BigDecimal 规范化、时间统一毫秒精度、二进制
 * 固定 Base64）后 SHA-256 稳定 hash。拒绝 DB 侧 CRC32/checksum（方言绑定、加密列语义耦合）。
 * <b>加密列按密文原样比对</b>（v1 密钥一致门禁前提下密文一致即正确，不解密扩大密钥面）；脱敏
 * 展示字段是 compute 派生属性，不进入比对面。失配明细（实体/主键/双侧 hash）必须落报告，
 * 不允许"总体一致"掩盖行级差异。</p>
 *
 * <p>时序门禁由编排器保证：校验只在写停止关 closed + lag=0 之后执行（校验器本身不做时钟假设，
 * 只读、可重复）。</p>
 */
public class ConsistencyVerifier {

    /** 抽样分子抽样判定：pkHash % denominator == 0。 */
    static final int MAX_MISMATCH_DETAILS = 10;
    private static final String NULL_MARK = "\u0001NULL";
    static final int CHECKSUM_BATCH = 2000;

    private final IJdbcTemplate jdbcTemplate;
    private final TargetShardMapping mapping;
    private final List<String> keyColumns;
    /** 2.5-MA-07（plan 2554 裁定 A）：keyset 分页批大小（缺省常量；测试缝可缩小批触发跨页归并）。 */
    private int checksumBatch = CHECKSUM_BATCH;

    public ConsistencyVerifier(IJdbcTemplate jdbcTemplate, TargetShardMapping mapping,
                               List<String> keyColumns) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapping = mapping;
        this.keyColumns = keyColumns;
    }

    /** 测试缝：缩小 keyset 分页批大小以覆盖跨页边界（生产路径恒用 CHECKSUM_BATCH）。 */
    void setChecksumBatch(int checksumBatch) {
        this.checksumBatch = checksumBatch;
    }

    /**
     * 校验报告。
     */
    public static final class VerifyReport {
        public long sourceCount;
        public long targetCount;
        public long mismatchCount;
        public final List<String> mismatchDetails = new ArrayList<>();
        public boolean countChecked;
        public boolean checksumChecked;
        public int sampledRows;

        public boolean isPassed() {
            return mismatchCount == 0 && sourceCount == targetCount;
        }
    }

    /**
     * 执行三级校验（按配置的开关组合），返回报告。校验失败以报告承载（是否升级为异常由编排器
     * 的门禁语义决定），本方法只对结构性错误抛类型化异常。
     */
    public VerifyReport verify(MigrationJobConfig config) {
        VerifyReport report = new VerifyReport();

        if (config.isVerifyCount()) {
            report.countChecked = true;
            report.sourceCount = sumCounts(mapping.getSourceTables());
            report.targetCount = sumCounts(mapping.getTargetTables());
            if (report.sourceCount != report.targetCount) {
                throw new NopException(ERR_MIG_COUNT_MISMATCH)
                        .param(ARG_TABLE_NAME, mapping.getBaseTableName())
                        .param(ARG_SOURCE_COUNT, report.sourceCount)
                        .param(ARG_TARGET_COUNT, report.targetCount);
            }
        }

        boolean needChecksum = config.isVerifyFullChecksum() || config.getVerifySampleRatio() > 0;
        if (needChecksum) {
            report.checksumChecked = true;
            int denominator = config.isVerifyFullChecksum() ? 1
                    : Math.max(1, (int) Math.round(1.0 / config.getVerifySampleRatio()));
            verifyChecksum(denominator, report);
            if (report.mismatchCount > 0) {
                throw new NopException(ERR_MIG_CHECKSUM_MISMATCH)
                        .param(ARG_TABLE_NAME, mapping.getBaseTableName())
                        .param(ARG_MISMATCH_COUNT, report.mismatchCount)
                        .param(ARG_MISMATCH_DETAIL, String.join("; ", report.mismatchDetails));
            }
        }
        return report;
    }

    private long sumCounts(List<PhysicalTable> tables) {
        long total = 0;
        for (PhysicalTable table : tables) {
            String sql = "SELECT COUNT(*) FROM " + escape(table);
            Long count = jdbcTemplate.findLong(SQL.begin().querySpace(table.getQuerySpace())
                    .name("orm-migration-count:" + table.getTableName()).sql(sql).end(), 0L);
            total += count == null ? 0L : count;
        }
        return total;
    }

    /**
     * 2.5-MA-07（plan 2554 裁定 A）：双侧 keyset 分页拉取 + 归并双游标对比——替换原「双侧全量
     * pk→hash Map + TreeSet 键集驻堆」形态。任一时刻堆内仅 O(N=checksumBatch) 行（每侧每物理
     * 表一个页缓冲），missing/added/mismatch 增量检出，失配报告契约不变。
     */
    private void verifyChecksum(int denominator, VerifyReport report) {
        try (SideCursor source = new SideCursor(mapping.getSourceTables(), denominator, report);
             SideCursor target = new SideCursor(mapping.getTargetTables(), denominator, report)) {
            Row s = source.next();
            Row t = target.next();
            while (s != null || t != null) {
                if (t == null || (s != null && s.key.compareTo(t.key) < 0)) {
                    // 源有目标无 = target missing
                    recordMismatch(s.key, s.hash, null, report);
                    s = source.next();
                } else if (s == null || s.key.compareTo(t.key) > 0) {
                    // 目标有源无 = target added
                    recordMismatch(t.key, null, t.hash, report);
                    t = target.next();
                } else {
                    if (!Objects.equals(s.hash, t.hash))
                        recordMismatch(s.key, s.hash, t.hash, report);
                    s = source.next();
                    t = target.next();
                }
            }
        }
    }

    private void recordMismatch(RowKey key, String sourceHash, String targetHash, VerifyReport report) {
        report.mismatchCount++;
        if (report.mismatchDetails.size() < MAX_MISMATCH_DETAILS) {
            report.mismatchDetails.add("pk=" + key.display
                    + ",source=" + (sourceHash == null ? "MISSING" : sourceHash)
                    + ",target=" + (targetHash == null ? "MISSING" : targetHash));
        }
    }

    /**
     * 行级比对面：keyset 分页键（原始主键值元组，与 DB ORDER BY 主键序一致）+ 规范化展示串
     * （canonical 拼接，与既有 pkKey 失配报告格式一致）。
     */
    static final class RowKey implements Comparable<RowKey> {
        final List<Object> parts;
        final String display;

        RowKey(List<Object> parts, String display) {
            this.parts = parts;
            this.display = display;
        }

        @Override
        public int compareTo(RowKey o) {
            int n = Math.min(parts.size(), o.parts.size());
            for (int i = 0; i < n; i++) {
                int cmp = compareKeyPart(parts.get(i), o.parts.get(i));
                if (cmp != 0)
                    return cmp;
            }
            return Integer.compare(parts.size(), o.parts.size());
        }
    }

    /** 键分量比较：数值经 BigDecimal 数值序（与 DB 数值序一致），同型 Comparable 原生序，其余回退规范化串序。 */
    static int compareKeyPart(Object a, Object b) {
        if (a == null && b == null)
            return 0;
        if (a == null)
            return -1;
        if (b == null)
            return 1;
        if (a instanceof Number && b instanceof Number) {
            return toBigDecimal((Number) a).compareTo(toBigDecimal((Number) b));
        }
        if (a.getClass() == b.getClass() && a instanceof Comparable) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            int cmp = ((Comparable) a).compareTo(b);
            return cmp;
        }
        return canonical(a).compareTo(canonical(b));
    }

    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal)
            return (BigDecimal) number;
        if (number instanceof Double || number instanceof Float)
            return new BigDecimal(number.toString());
        return new BigDecimal(number.toString());
    }

    /** 单侧比对面行（合并同键重复行后的 hash 证据）。 */
    static final class Row {
        final RowKey key;
        final String hash;

        Row(RowKey key, String hash) {
            this.key = key;
            this.hash = hash;
        }
    }

    /**
     * 单物理表 keyset 分页游标：{@code WHERE pk > lastKey ORDER BY pk LIMIT N}（消 offset 深
     * 分页二次方 IO，裁定 A）。堆内仅持一个页缓冲。
     */
    private final class TableCursor {
        private final PhysicalTable table;
        private final IDialect dialect;
        private final String order;
        private List<Map<String, Object>> page = List.of();
        private int pos;
        private List<Object> lastKey;
        private boolean exhausted;
        private Row current;

        TableCursor(PhysicalTable table) {
            this.table = table;
            this.dialect = jdbcTemplate.getDialectForQuerySpace(table.getQuerySpace());
            this.order = orderClause(table);
        }

        /** 前进到下一个抽样子集内的行（跨页 keyset 拉取；null = 本表扫尽）。 */
        Row next(int denominator, VerifyReport report) {
            while (true) {
                if (pos >= page.size()) {
                    if (exhausted)
                        return finish();
                    fetchPage();
                    continue;
                }
                Map<String, Object> row = page.get(pos++);
                RowKey key = keyOf(row);
                if (denominator > 1 && Math.floorMod(stableHash(key.display), denominator) != 0)
                    continue;
                report.sampledRows++;
                current = new Row(key, rowHash(row));
                return current;
            }
        }

        private Row finish() {
            current = null;
            return null;
        }

        private void fetchPage() {
            SQL.SqlBuilder sb = SQL.begin().querySpace(table.getQuerySpace())
                    .name("orm-migration-scan:" + table.getTableName())
                    .sql("SELECT * FROM ").sql(escape(table));
            if (lastKey != null) {
                sb.sql(" WHERE ");
                if (keyColumns.size() == 1) {
                    sb.sql(dialect.escapeSQLName(keyColumns.get(0))).sql(" > ").param(lastKey.get(0));
                } else {
                    // 行值构造器 keyset（H2/mysql8+/mariadb10.2+/postgres 支持）
                    for (int i = 0; i < keyColumns.size(); i++) {
                        sb.sql(i == 0 ? "(" : ",").sql(dialect.escapeSQLName(keyColumns.get(i)));
                    }
                    for (int i = 0; i < lastKey.size(); i++) {
                        sb.sql(i == 0 ? ") > (" : ",").param(lastKey.get(i));
                    }
                    sb.sql(")");
                }
            }
            sb.sql(" ORDER BY ").sql(order);
            SQL paged = dialect.getPaginationHandler()
                    .getPagedSql(new LongRangeBean(0, checksumBatch), sb.end());
            page = jdbcTemplate.findAll(paged);
            pos = 0;
            if (page.isEmpty()) {
                exhausted = true;
                return;
            }
            Map<String, Object> lastRow = page.get(page.size() - 1);
            List<Object> parts = new ArrayList<>(keyColumns.size());
            for (String col : keyColumns) {
                parts.add(lookupIgnoreCase(lastRow, col));
            }
            lastKey = parts;
            if (page.size() < checksumBatch)
                exhausted = true;
        }

        private RowKey keyOf(Map<String, Object> row) {
            List<Object> parts = new ArrayList<>(keyColumns.size());
            for (String col : keyColumns) {
                parts.add(lookupIgnoreCase(row, col));
            }
            return new RowKey(parts, pkKey(row));
        }
    }

    /**
     * 单侧（源或目标）归并游标：跨物理表 k 路归并（堆序 = 主键序），同键重复行（异常数据）
     * 以 DUP 标记合并 hash——与旧全量 Map merge 语义一致。
     */
    private final class SideCursor implements AutoCloseable {
        private final PriorityQueue<TableCursor> heap;
        private final int denominator;
        private final VerifyReport report;

        SideCursor(List<PhysicalTable> tables, int denominator, VerifyReport report) {
            this.denominator = denominator;
            this.report = report;
            this.heap = new PriorityQueue<>((a, b) -> {
                if (a.current == null && b.current == null)
                    return 0;
                if (a.current == null)
                    return 1;
                if (b.current == null)
                    return -1;
                return a.current.key.compareTo(b.current.key);
            });
            for (PhysicalTable table : tables) {
                TableCursor cursor = new TableCursor(table);
                if (cursor.next(denominator, report) != null)
                    heap.add(cursor);
            }
        }

        /** 下一个唯一键行（同键重复行合并 DUP hash 后作为一个键产出）。 */
        Row next() {
            Row first = pollRow();
            if (first == null)
                return null;
            String hash = first.hash;
            Row dup = peekRow();
            while (dup != null && dup.key.compareTo(first.key) == 0) {
                pollRow();
                hash = hash.equals(dup.hash) ? hash : "DUP:" + hash + "|" + dup.hash;
                dup = peekRow();
            }
            return new Row(first.key, hash);
        }

        private Row pollRow() {
            TableCursor cursor = heap.poll();
            if (cursor == null)
                return null;
            Row row = cursor.current;
            if (cursor.next(denominator, report) != null)
                heap.add(cursor);
            return row;
        }

        private Row peekRow() {
            TableCursor cursor = heap.peek();
            return cursor == null ? null : cursor.current;
        }

        @Override
        public void close() {
            heap.clear();
        }
    }

    private String orderClause(PhysicalTable table) {
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(table.getQuerySpace());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyColumns.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append(dialect.escapeSQLName(keyColumns.get(i)));
        }
        if (keyColumns.isEmpty())
            throw new NopException(MigrationErrors.ERR_MIG_CONFIG_INVALID)
                    .param(MigrationErrors.ARG_REASON, "checksum requires key columns for entity "
                            + mapping.getEntityName());
        return sb.toString();
    }

    private String escape(PhysicalTable table) {
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(table.getQuerySpace());
        return dialect.escapeSQLName(table.getTableName());
    }

    private String pkKey(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyColumns.size(); i++) {
            if (i > 0)
                sb.append('\u0003');
            sb.append(canonical(lookupIgnoreCase(row, keyColumns.get(i))));
        }
        return sb.toString();
    }

    static Object lookupIgnoreCase(Map<String, Object> row, String name) {
        Object direct = row.get(name);
        if (direct != null)
            return direct;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name))
                return entry.getValue();
        }
        return null;
    }

    /**
     * 行 hash：全部物理列按列名排序后规范化拼接，SHA-256 十六进制。enc 主列是物理列值，原样进入
     * 规范化串（密文比对）；assisted/like 辅助列同物理列参与比对。
     */
    static String rowHash(Map<String, Object> row) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            sorted.put(entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT),
                    entry.getValue());
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            sb.append(entry.getKey()).append('=').append(canonical(entry.getValue())).append('\u0002');
        }
        return sha256Hex(sb.toString());
    }

    /**
     * 物理列值规范化串（设计 §4.6）：null 专用标记 / 数值 BigDecimal 规范化 / 时间统一毫秒精度
     * （按 UTC 墙钟换算——Timestamp/LocalDateTime 等价归一，跨 JVM 时区稳定）/ 二进制固定 Base64 /
     * 其余 toString。类型标签防跨型碰撞。
     */
    static String canonical(Object value) {
        if (value == null)
            return NULL_MARK;
        if (value instanceof BigDecimal)
            return "n:" + ((BigDecimal) value).stripTrailingZeros().toPlainString();
        if (value instanceof Double || value instanceof Float)
            return "n:" + new BigDecimal(((Number) value).doubleValue()).stripTrailingZeros().toPlainString();
        if (value instanceof Number)
            return "n:" + new BigDecimal(((Number) value).toString()).stripTrailingZeros().toPlainString();
        if (value instanceof Boolean)
            return "b:" + ((Boolean) value ? 1 : 0);
        if (value instanceof byte[])
            return "z:" + Base64.getEncoder().encodeToString((byte[]) value);
        if (value instanceof Timestamp)
            return canonical(((Timestamp) value).toLocalDateTime());
        if (value instanceof java.sql.Date)
            return "t:" + ((java.sql.Date) value).toLocalDate().toEpochDay();
        if (value instanceof java.sql.Time)
            return "t:" + ((java.sql.Time) value).toLocalTime().toNanoOfDay();
        if (value instanceof java.time.LocalDateTime)
            return "t:" + ((java.time.LocalDateTime) value).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        if (value instanceof java.time.LocalDate)
            return "t:" + ((java.time.LocalDate) value).toEpochDay();
        return "s:" + value;
    }

    static int stableHash(String s) {
        // String.hashCode 是 JLS 跨 JVM 稳定值（同 DebeziumCdcSourceFunction.shardOf 先例）
        return s.hashCode();
    }

    static String sha256Hex(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
