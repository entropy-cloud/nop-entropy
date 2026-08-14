package io.nop.datav.service.alert;

import io.nop.api.core.exceptions.NopException;

import java.math.BigDecimal;

import static io.nop.datav.service.NopDatavErrors.ARG_ALERT_OPERATOR;
import static io.nop.datav.service.NopDatavErrors.ARG_ALERT_RULE_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_THRESHOLD_VALUE;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_INVALID_THRESHOLD;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_UNSUPPORTED_OPERATOR;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_VALUE_REQUIRED;

/**
 * 告警阈值比较器（D5-2，schedule-report-design.md §14）。
 *
 * <p>按 operator（gt/gte/lt/lte/eq/neq/between）比较 currentValue 与 threshold。
 * between 用 [thresholdValue, thresholdValue2]，thresholdValue2 为 null → 显式抛错。</p>
 *
 * <p>本类为无状态纯函数，便于单元测试直接覆盖。</p>
 */
public final class AlertThresholdComparator {

    /** operator 字典值（与 dict {@code datav/alert-operator} 对齐） */
    public static final String OP_GT = "gt";
    public static final String OP_GTE = "gte";
    public static final String OP_LT = "lt";
    public static final String OP_LTE = "lte";
    public static final String OP_EQ = "eq";
    public static final String OP_NEQ = "neq";
    public static final String OP_BETWEEN = "between";

    private AlertThresholdComparator() {
    }

    /**
     * 比较 currentValue 是否满足阈值条件。
     *
     * @param currentValue    评估后的标量（非 null）
     * @param operator        比较运算符
     * @param thresholdValue  主阈值（非 null）
     * @param thresholdValue2 副阈值（仅 between 用；为 null 且 operator=between 时抛错）
     * @param alertRuleId     规则 ID（错误参数用）
     * @return true = 条件满足（应触发告警）
     */
    public static boolean compare(BigDecimal currentValue, String operator,
                                  BigDecimal thresholdValue, BigDecimal thresholdValue2,
                                  String alertRuleId) {
        if (currentValue == null || thresholdValue == null) {
            throw new NopException(ERR_DATAV_ALERT_VALUE_REQUIRED)
                    .param(ARG_ALERT_RULE_ID, alertRuleId);
        }
        String op = operator == null ? "" : operator.toLowerCase();
        switch (op) {
            case OP_GT:
                return currentValue.compareTo(thresholdValue) > 0;
            case OP_GTE:
                return currentValue.compareTo(thresholdValue) >= 0;
            case OP_LT:
                return currentValue.compareTo(thresholdValue) < 0;
            case OP_LTE:
                return currentValue.compareTo(thresholdValue) <= 0;
            case OP_EQ:
                return currentValue.compareTo(thresholdValue) == 0;
            case OP_NEQ:
                return currentValue.compareTo(thresholdValue) != 0;
            case OP_BETWEEN:
                if (thresholdValue2 == null) {
                    throw new NopException(ERR_DATAV_ALERT_INVALID_THRESHOLD)
                            .param(ARG_ALERT_RULE_ID, alertRuleId)
                            .param(ARG_THRESHOLD_VALUE, thresholdValue);
                }
                return currentValue.compareTo(thresholdValue) >= 0
                        && currentValue.compareTo(thresholdValue2) <= 0;
            default:
                // 未知 operator → 显式失败（非静默）
                throw new NopException(ERR_DATAV_ALERT_UNSUPPORTED_OPERATOR)
                        .param(ARG_ALERT_RULE_ID, alertRuleId)
                        .param(ARG_ALERT_OPERATOR, operator);
        }
    }
}
