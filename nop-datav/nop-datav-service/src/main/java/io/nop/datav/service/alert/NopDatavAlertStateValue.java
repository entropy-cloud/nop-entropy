package io.nop.datav.service.alert;

/**
 * 告警状态机两态枚举（D5-2，schedule-report-design.md §15）。
 *
 * <p>{@code state} 列只持久化 {@link #OK} / {@link #TRIGGERED} 两态。
 * {@code RESOLVED} <b>不入此枚举</b>——它是 {@code TRIGGERED → OK} 转换时的
 * 一次性恢复通知动作（瞬态），不持久化为独立状态。</p>
 *
 * <p>dict {@code datav/alert-state}（valueType=string）取值与本枚举 {@link #getValue()} 对齐：
 * {@code OK}/{@code TRIGGERED}（大写，与 dict value 完全一致）。</p>
 */
public enum NopDatavAlertStateValue {

    /** 正常态：条件不满足或已恢复。 */
    OK("OK"),

    /** 已触发态：条件满足。 */
    TRIGGERED("TRIGGERED");

    /** dict value（与 {@code datav/alert-state} dict 的 value 字段对齐） */
    private final String value;

    NopDatavAlertStateValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static boolean isTriggered(String state) {
        return TRIGGERED.value.equalsIgnoreCase(state);
    }

    public static boolean isOk(String state) {
        return OK.value.equalsIgnoreCase(state);
    }
}
