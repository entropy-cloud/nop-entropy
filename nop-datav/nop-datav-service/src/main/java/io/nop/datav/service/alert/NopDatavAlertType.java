package io.nop.datav.service.alert;

/**
 * 告警通知类型（D5-2，schedule-report-design.md §15/§17）。
 *
 * <p>对应状态机转换时的通知动作：
 * <ul>
 *   <li>{@link #TRIGGER}：OK → TRIGGERED 转换（条件首次满足），或 TRIGGERED 持续 + rearm 冷静期到期重发</li>
 *   <li>{@link #RECOVER}：TRIGGERED → OK 转换（条件不再满足，发送恢复通知）</li>
 * </ul>
 * </p>
 */
public enum NopDatavAlertType {

    /** 告警触发通知（含首次触发 + rearm 重发） */
    TRIGGER("trigger"),

    /** 告警恢复通知（TRIGGERED → OK 转换时） */
    RECOVER("recover");

    private final String value;

    NopDatavAlertType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
