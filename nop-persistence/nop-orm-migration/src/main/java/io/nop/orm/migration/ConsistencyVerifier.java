/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.migration.TargetShardMapping.PhysicalTable;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

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
    private static final int CHECKSUM_BATCH = 2000;

    private final IJdbcTemplate jdbcTemplate;
    private final TargetShardMapping mapping;
    private final List<String> keyColumns;

    public ConsistencyVerifier(IJdbcTemplate jdbcTemplate, TargetShardMapping mapping,
                               List<String> keyColumns) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapping = mapping;
        this.keyColumns = keyColumns;
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

    private void verifyChecksum(int denominator, VerifyReport report) {
        Map<String, String> sourceHashes = scanHashes(mapping.getSourceTables(), denominator, report);
        Map<String, String> targetHashes = scanHashes(mapping.getTargetTables(), denominator, report);

        TreeSet<String> allKeys = new TreeSet<>();
        allKeys.addAll(sourceHashes.keySet());
        allKeys.addAll(targetHashes.keySet());

        for (String pk : allKeys) {
            String sourceHash = sourceHashes.get(pk);
            String targetHash = targetHashes.get(pk);
            if (!Objects.equals(sourceHash, targetHash)) {
                report.mismatchCount++;
                if (report.mismatchDetails.size() < MAX_MISMATCH_DETAILS) {
                    report.mismatchDetails.add("pk=" + pk
                            + ",source=" + (sourceHash == null ? "MISSING" : sourceHash)
                            + ",target=" + (targetHash == null ? "MISSING" : targetHash));
                }
            }
        }
    }

    /**
     * 逐物理表 offset 分页扫行（主键序，与 EncAssistedBackfiller 同族分页先例），按 pk 归并出
     * 键 → 行 hash 映射。抽样子集：stableHash(pk) % denominator == 0。
     */
    private Map<String, String> scanHashes(List<PhysicalTable> tables, int denominator, VerifyReport report) {
        Map<String, String> result = new HashMap<>();
        for (PhysicalTable table : tables) {
            long offset = 0;
            while (true) {
                List<Map<String, Object>> rows = findPage(table, offset);
                if (rows.isEmpty())
                    break;
                for (Map<String, Object> row : rows) {
                    String pk = pkKey(row);
                    if (denominator > 1 && Math.floorMod(stableHash(pk), denominator) != 0)
                        continue;
                    report.sampledRows++;
                    result.merge(pk, rowHash(row), (a, b) -> {
                        // 跨物理表同 pk（异常数据）直接视为失配证据
                        return a.equals(b) ? a : "DUP:" + a + "|" + b;
                    });
                }
                if (rows.size() < CHECKSUM_BATCH)
                    break;
                offset += CHECKSUM_BATCH;
            }
        }
        return result;
    }

    private List<Map<String, Object>> findPage(PhysicalTable table, long offset) {
        String order = orderClause(table);
        String sql = "SELECT * FROM " + escape(table) + " ORDER BY " + order;
        return jdbcTemplate.findPage(SQL.begin().querySpace(table.getQuerySpace())
                .name("orm-migration-scan:" + table.getTableName())
                .sql(sql).end(), offset, CHECKSUM_BATCH);
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
