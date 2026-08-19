package io.nop.xlang.compare;

/**
 * 期望列缺席的显式记录（含原因），不计入通过。
 */
public final class ColumnSkipRecord {
    public static final String REASON_NOT_REGISTERED = "backend-column-not-registered";
    public static final String REASON_CAPABILITY_NOT_DECLARED = "column-capability-not-declared-for-unit-kind";

    private final String backendId;
    private final String reason;

    public ColumnSkipRecord(String backendId, String reason) {
        this.backendId = backendId;
        this.reason = reason;
    }

    public String getBackendId() {
        return backendId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "skipped[" + backendId + ": " + reason + "]";
    }
}
