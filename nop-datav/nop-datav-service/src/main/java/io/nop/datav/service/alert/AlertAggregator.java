package io.nop.datav.service.alert;

import io.nop.api.core.exceptions.NopException;
import io.nop.datav.biz.PanelDataResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ARG_ALERT_RULE_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_CURRENT_VALUE;
import static io.nop.datav.service.NopDatavErrors.ARG_VALUE_FIELD;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_VALUE_NOT_NUMERIC;

/**
 * 告警标量聚合器（D5-2，schedule-report-design.md §13）。
 *
 * <p>从 {@link PanelDataResult} 按 {@code valueField} + {@code aggregation} 求标量 BigDecimal。
 * 非数值类型 / 列不存在 → 显式抛 {@link NopException}（非静默跳过）。</p>
 *
 * <p>本类为无状态纯函数集合，方法经 {@link AlertEvaluator} 调用，便于单元测试直接覆盖。</p>
 */
public final class AlertAggregator {

    /** aggregation 字典值（与 dict {@code datav/alert-aggregation} 对齐） */
    public static final String AGG_NONE = "none";
    public static final String AGG_FIRST = "first";
    public static final String AGG_SUM = "sum";
    public static final String AGG_AVG = "avg";
    public static final String AGG_MIN = "min";
    public static final String AGG_MAX = "max";
    public static final String AGG_COUNT = "count";

    /** 默认聚合（rule.aggregation 为空时取首行） */
    public static final String DEFAULT_AGGREGATION = AGG_FIRST;

    private AlertAggregator() {
    }

    /**
     * 标量聚合结果。{@code null} 表示「无数据行视为条件不满足」语义。
     */
    public static final class ScalarResult {
        /** 聚合后的标量值；null 表示无数据行（条件不满足）。 */
        public final BigDecimal value;
        /** 评估的行数（count 聚合时等于 value，其他聚合时仅审计用）。 */
        public final int rowCount;

        public ScalarResult(BigDecimal value, int rowCount) {
            this.value = value;
            this.rowCount = rowCount;
        }

        public boolean hasValue() {
            return value != null;
        }
    }

    /**
     * 按 valueField + aggregation 求标量。
     *
     * @param data         面板数据结果
     * @param valueField   取值列名
     * @param aggregation  聚合方式（none/first/sum/avg/min/max/count，空则取 first）
     * @param alertRuleId  告警规则 ID（错误参数用）
     * @return 标量结果；无数据行时 {@code value=null, rowCount=0}
     */
    public static ScalarResult aggregate(PanelDataResult data, String valueField,
                                         String aggregation, String alertRuleId) {
        String agg = aggregation == null || aggregation.isEmpty() ? DEFAULT_AGGREGATION : aggregation;
        List<Map<String, Object>> rows = data == null ? null : data.getRows();
        int rowCount = rows == null ? 0 : rows.size();

        // count 聚合：返回行数（不读 valueField 列）
        if (AGG_COUNT.equals(agg)) {
            if (rowCount == 0) {
                return new ScalarResult(null, 0);
            }
            return new ScalarResult(BigDecimal.valueOf(rowCount), rowCount);
        }

        if (rowCount == 0) {
            // 无数据行 → 视为条件不满足（不触发、不抛错）
            return new ScalarResult(null, 0);
        }

        // 列存在性校验 + 解析实际列名（row map key 大小写可能不一致，需对齐）
        String actualColumn = resolveColumn(data, valueField);
        if (actualColumn == null) {
            throw new NopException(ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND)
                    .param(ARG_VALUE_FIELD, valueField)
                    .param(ARG_ALERT_RULE_ID, alertRuleId);
        }

        switch (agg) {
            case AGG_NONE:
            case AGG_FIRST:
                return new ScalarResult(toBigDecimal(rows.get(0).get(actualColumn), valueField, alertRuleId), rowCount);
            case AGG_SUM: {
                BigDecimal sum = BigDecimal.ZERO;
                for (Map<String, Object> row : rows) {
                    sum = sum.add(toBigDecimal(row.get(actualColumn), valueField, alertRuleId));
                }
                return new ScalarResult(sum, rowCount);
            }
            case AGG_AVG: {
                BigDecimal sum = BigDecimal.ZERO;
                for (Map<String, Object> row : rows) {
                    sum = sum.add(toBigDecimal(row.get(actualColumn), valueField, alertRuleId));
                }
                // scale=4 + ROUND_HALF_UP（与 threshold DECIMAL(20,4) 精度对齐）
                return new ScalarResult(sum.divide(BigDecimal.valueOf(rowCount), 4, BigDecimal.ROUND_HALF_UP), rowCount);
            }
            case AGG_MIN: {
                BigDecimal min = null;
                for (Map<String, Object> row : rows) {
                    BigDecimal v = toBigDecimal(row.get(actualColumn), valueField, alertRuleId);
                    if (min == null || v.compareTo(min) < 0) {
                        min = v;
                    }
                }
                return new ScalarResult(min, rowCount);
            }
            case AGG_MAX: {
                BigDecimal max = null;
                for (Map<String, Object> row : rows) {
                    BigDecimal v = toBigDecimal(row.get(actualColumn), valueField, alertRuleId);
                    if (max == null || v.compareTo(max) > 0) {
                        max = v;
                    }
                }
                return new ScalarResult(max, rowCount);
            }
            default:
                // 未知聚合方式 → 视为配置错误，抛错（非静默）
                throw new IllegalArgumentException(
                        "Unsupported aggregation: " + agg + " (alertRuleId=" + alertRuleId + ")");
        }
    }

    /**
     * 解析 valueField 在 PanelDataResult.columns 中的实际列名（大小写不敏感匹配）。
     * 返回 null 表示列不存在。
     */
    private static String resolveColumn(PanelDataResult data, String valueField) {
        if (data == null || data.getColumns() == null || valueField == null) {
            return null;
        }
        for (String col : data.getColumns()) {
            if (col != null && col.equalsIgnoreCase(valueField)) {
                return col;
            }
        }
        // 兜底：columns 列表可能缺失别名映射，直接检查首行 map key
        List<Map<String, Object>> rows = data.getRows();
        if (rows != null && !rows.isEmpty()) {
            for (Object key : rows.get(0).keySet()) {
                if (key != null && key.toString().equalsIgnoreCase(valueField)) {
                    return key.toString();
                }
            }
        }
        return null;
    }

    private static BigDecimal toBigDecimal(Object value, String valueField, String alertRuleId) {
        if (value == null) {
            throw new NopException(ERR_DATAV_ALERT_VALUE_NOT_NUMERIC)
                    .param(ARG_CURRENT_VALUE, "null")
                    .param(ARG_VALUE_FIELD, valueField)
                    .param(ARG_ALERT_RULE_ID, alertRuleId);
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            // 经 String 构造 BigDecimal 避免浮点精度损失（如 Double.toString）
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                throw new NopException(ERR_DATAV_ALERT_VALUE_NOT_NUMERIC)
                        .param(ARG_CURRENT_VALUE, value)
                        .param(ARG_VALUE_FIELD, valueField)
                        .param(ARG_ALERT_RULE_ID, alertRuleId);
            }
        }
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                throw new NopException(ERR_DATAV_ALERT_VALUE_NOT_NUMERIC)
                        .param(ARG_CURRENT_VALUE, value)
                        .param(ARG_VALUE_FIELD, valueField)
                        .param(ARG_ALERT_RULE_ID, alertRuleId);
            }
        }
        throw new NopException(ERR_DATAV_ALERT_VALUE_NOT_NUMERIC)
                .param(ARG_CURRENT_VALUE, String.valueOf(value))
                .param(ARG_VALUE_FIELD, valueField)
                .param(ARG_ALERT_RULE_ID, alertRuleId);
    }
}
