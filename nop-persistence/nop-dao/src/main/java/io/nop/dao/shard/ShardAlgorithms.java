/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.shard;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.dao.DaoErrors.ARG_REASON;
import static io.nop.dao.DaoErrors.ERR_DAO_SHARD_ALGORITHM_FAIL;
import static io.nop.dao.DaoErrors.ERR_DAO_SHARD_CONFIG_INVALID;

/**
 * 内置分片算法实现集（plan 2320，设计文档 §3.3）：mod / hash-mod / range / interval / inline。
 * 均为无状态对象，由 {@link ShardStrategy} 持有。
 */
public class ShardAlgorithms {

    private ShardAlgorithms() {
    }

    /**
     * 数值取模：{@code floorMod(value, shard-count)}。负数分片键映射到非负分片名。
     */
    public static class ModShardAlgorithm implements IShardAlgorithm {
        private final int shardCount;

        public ModShardAlgorithm(int shardCount) {
            this.shardCount = shardCount;
        }

        @Override
        public String selectShard(String shardProp, Object shardValue) {
            return String.valueOf(Math.floorMod(toLong(shardValue), shardCount));
        }

        @Override
        public List<String> shardNames() {
            List<String> ret = new ArrayList<>(shardCount);
            for (int i = 0; i < shardCount; i++) {
                ret.add(String.valueOf(i));
            }
            return ret;
        }
    }

    /**
     * 稳定hash取模：对分片值的字符串表示做FNV-1a 32位hash后取模。
     * 不依赖{@link String#hashCode()}（跨JVM版本稳定性存疑）也不依赖数据库hash，
     * 保证跨方言、跨进程同值同分片。
     */
    public static class HashModShardAlgorithm implements IShardAlgorithm {
        private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
        private static final int FNV_PRIME = 0x01000193;

        private final int shardCount;

        public HashModShardAlgorithm(int shardCount) {
            this.shardCount = shardCount;
        }

        static int fnv1a(String text) {
            int hash = FNV_OFFSET_BASIS;
            for (int i = 0, n = text.length(); i < n; i++) {
                hash ^= text.charAt(i);
                hash *= FNV_PRIME;
            }
            return hash;
        }

        @Override
        public String selectShard(String shardProp, Object shardValue) {
            return String.valueOf(Math.floorMod(fnv1a(String.valueOf(shardValue)), shardCount));
        }

        @Override
        public List<String> shardNames() {
            List<String> ret = new ArrayList<>(shardCount);
            for (int i = 0; i < shardCount; i++) {
                ret.add(String.valueOf(i));
            }
            return ret;
        }
    }

    /**
     * 显式边界范围映射：{@code ranges: {"1000000": "0", "2000000": "1"}}，
     * 语义为 {@code value <= 1000000 -> "0"}、{@code (1000000, 2000000] -> "1"}。
     * 值超出全部边界时显式抛错（不允许静默落某一分片）。
     */
    public static class RangeShardAlgorithm implements IShardAlgorithm {
        private final TreeMap<Long, String> ranges;

        public RangeShardAlgorithm(Map<String, Object> ranges) {
            if (ranges == null || ranges.isEmpty())
                throw shardConfigInvalid("range algorithm requires non-empty 'ranges' mapping");
            this.ranges = new TreeMap<>();
            for (Map.Entry<String, Object> entry : ranges.entrySet()) {
                Long upper = toLongStrict(entry.getKey(), "ranges key");
                String shardName = String.valueOf(entry.getValue());
                this.ranges.put(upper, shardName);
            }
        }

        @Override
        public String selectShard(String shardProp, Object shardValue) {
            long value = toLong(shardValue);
            Map.Entry<Long, String> entry = ranges.ceilingEntry(value);
            if (entry == null)
                throw shardValueOutOfRange(shardValue);
            return entry.getValue();
        }

        @Override
        public List<String> shardNames() {
            return new ArrayList<>(new java.util.LinkedHashSet<>(ranges.values()));
        }
    }

    /**
     * 时间区间分片：按 {@code interval-step}（如{@code 1d}/{@code 2M}/{@code 1y}）从
     * {@code datetime-lower} 起切分区间，分片名为 {@code format}（DateTimeFormatter pattern，
     * 缺省 {@code yyyyMMdd}）格式化的区间起始日期。值超出 {@code datetime-lower/upper} 范围时显式抛错。
     */
    public static class IntervalShardAlgorithm implements IShardAlgorithm {
        private final java.time.LocalDate lower;
        private final java.time.LocalDate upper;
        private final int stepCount;
        private final char stepUnit; // d / M / y
        private final java.time.format.DateTimeFormatter formatter;

        public IntervalShardAlgorithm(String intervalStep, String datetimeLower, String datetimeUpper, String format) {
            if (datetimeLower == null || datetimeLower.isEmpty())
                throw shardConfigInvalid("interval algorithm requires 'datetime-lower'");
            this.lower = parseDate(datetimeLower);
            this.upper = datetimeUpper == null || datetimeUpper.isEmpty() ? null : parseDate(datetimeUpper);
            int[] step = parseStep(intervalStep);
            this.stepCount = step[0];
            this.stepUnit = (char) step[1];
            this.formatter = java.time.format.DateTimeFormatter
                    .ofPattern(format == null || format.isEmpty() ? "yyyyMMdd" : format);
        }

        @Override
        public String selectShard(String shardProp, Object shardValue) {
            java.time.LocalDate date = toDate(shardValue);
            if (date.isBefore(lower) || (upper != null && date.isAfter(upper)))
                throw shardValueOutOfRange(shardValue);
            return formatter.format(intervalStart(date));
        }

        java.time.LocalDate intervalStart(java.time.LocalDate date) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(lower, date);
            switch (stepUnit) {
                case 'd':
                    return lower.plusDays((days / stepCount) * stepCount);
                case 'M': {
                    long months = java.time.temporal.ChronoUnit.MONTHS.between(lower.withDayOfMonth(1),
                            date.withDayOfMonth(1));
                    return lower.withDayOfMonth(1).plusMonths((months / stepCount) * stepCount);
                }
                case 'y': {
                    long years = (long) date.getYear() - lower.getYear();
                    return lower.withDayOfYear(1).plusYears((years / stepCount) * stepCount);
                }
                default:
                    throw shardConfigInvalid("unsupported interval step unit: " + stepUnit);
            }
        }

        @Override
        public List<String> shardNames() {
            List<String> ret = new ArrayList<>();
            java.time.LocalDate cur = lower;
            int iterated = 0;
            while (upper == null || !cur.isAfter(upper)) {
                // 同一分片名（如format只含年份）不重复收集
                String name = formatter.format(cur);
                if (ret.isEmpty() || !name.equals(ret.get(ret.size() - 1)))
                    ret.add(name);
                cur = nextStep(cur);
                // 防御：迭代上限按候选区间数计算，避免format退化（如仅含年份）导致的超长循环
                if (++iterated > 100000)
                    throw shardConfigInvalid("interval shard count exceeds limit, check 'interval-step' config");
            }
            return ret;
        }

        private java.time.LocalDate nextStep(java.time.LocalDate cur) {
            switch (stepUnit) {
                case 'd':
                    return cur.plusDays(stepCount);
                case 'M':
                    return cur.plusMonths(stepCount);
                case 'y':
                    return cur.plusYears(stepCount);
                default:
                    throw shardConfigInvalid("unsupported interval step unit: " + stepUnit);
            }
        }
    }

    /**
     * 表达式分片：{@code algorithm-expression} 为XScript表达式，求值上下文变量
     * {@code value}（分片值）与{@code shardProp}（分片键列名），表达式返回分片名
     * （字符串或数字）。求值先例对齐 DefaultDataAuthChecker 的XLang配置求值。
     * 分片名全集无法静态枚举，须由策略显式配置 {@code shard-names}。
     */
    public static class InlineShardAlgorithm implements IShardAlgorithm {
        private final ExprEvalAction action;
        private final String expression;

        public InlineShardAlgorithm(String expression) {
            if (expression == null || expression.isEmpty())
                throw shardConfigInvalid("inline algorithm requires 'algorithm-expression'");
            this.expression = expression;
            this.action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                    .compileFullExpr(null, expression);
        }

        @Override
        public String selectShard(String shardProp, Object shardValue) {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue("value", shardValue);
            scope.setLocalValue("shardProp", shardProp);
            Object result = action.invoke(scope);
            if (result == null)
                throw new NopException(ERR_DAO_SHARD_ALGORITHM_FAIL)
                        .param(ARG_REASON,
                                "inline expression '" + expression + "' returned null for value " + shardValue);
            return String.valueOf(result);
        }

        @Override
        public List<String> shardNames() {
            throw shardConfigInvalid(
                    "inline algorithm cannot enumerate shard names, config 'shard-names' explicitly");
        }

        public String getExpression() {
            return expression;
        }
    }

    static long toLong(Object value) {
        if (value == null)
            throw new NopException(ERR_DAO_SHARD_ALGORITHM_FAIL)
                    .param(ARG_REASON, "shard value must not be null");
        Long v = toLongNullable(value);
        if (v == null)
            throw new NopException(ERR_DAO_SHARD_ALGORITHM_FAIL)
                    .param(ARG_REASON,
                            "shard value must be numeric but got: " + value.getClass().getName() + " " + value);
        return v;
    }

    static Long toLongNullable(Object value) {
        if (value instanceof Number)
            return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static Long toLongStrict(String text, String what) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw shardConfigInvalid(what + " must be numeric: " + text);
        }
    }

    static java.time.LocalDate parseDate(String text) {
        try {
            return java.time.LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (java.time.format.DateTimeParseException e) {
            throw shardConfigInvalid("invalid date (expected yyyy-MM-dd): " + text);
        }
    }

    static java.time.LocalDate toDate(Object value) {
        if (value instanceof java.time.LocalDate)
            return (java.time.LocalDate) value;
        if (value instanceof java.time.LocalDateTime)
            return ((java.time.LocalDateTime) value).toLocalDate();
        if (value instanceof java.util.Date)
            return ((java.util.Date) value).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        if (value instanceof String)
            return parseDate((String) value);
        throw new NopException(ERR_DAO_SHARD_ALGORITHM_FAIL)
                .param(ARG_REASON,
                        "shard value must be date/time but got: " + value.getClass().getName() + " " + value);
    }

    static int[] parseStep(String intervalStep) {
        if (intervalStep == null || intervalStep.length() < 2)
            throw shardConfigInvalid("invalid 'interval-step' (expected like 1d/2M/1y): " + intervalStep);
        int count;
        char unit = intervalStep.charAt(intervalStep.length() - 1);
        try {
            count = Integer.parseInt(intervalStep.substring(0, intervalStep.length() - 1));
        } catch (NumberFormatException e) {
            throw shardConfigInvalid("invalid 'interval-step' (expected like 1d/2M/1y): " + intervalStep);
        }
        if (count <= 0 || (unit != 'd' && unit != 'M' && unit != 'y'))
            throw shardConfigInvalid("invalid 'interval-step' (expected like 1d/2M/1y): " + intervalStep);
        return new int[]{count, unit};
    }

    static NopException shardConfigInvalid(String reason) {
        return new NopException(ERR_DAO_SHARD_CONFIG_INVALID)
                .param(ARG_REASON, reason);
    }

    static NopException shardValueOutOfRange(Object value) {
        return new NopException(ERR_DAO_SHARD_ALGORITHM_FAIL)
                .param(ARG_REASON, "shard value out of configured range: " + value);
    }
}
