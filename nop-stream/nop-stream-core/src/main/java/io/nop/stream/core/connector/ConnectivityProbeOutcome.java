/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector;

/**
 * Outcome of one dry-run connectivity probe (item 20 / P-REQ-13). {@link #PASS} means
 * the probe contract executed successfully; {@link #FAIL} carries the explicit error
 * detail (error configuration must surface a typed error code, never a silent pass);
 * {@link #SKIP} is the honest "no probe contract implemented — connectivity
 * undetermined" report item for endpoints without any probe contract.
 */
public final class ConnectivityProbeOutcome {

    public enum Status {PASS, FAIL, SKIP}

    private final Status status;
    private final String errorCode;
    private final String detail;

    private ConnectivityProbeOutcome(Status status, String errorCode, String detail) {
        this.status = status;
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public static ConnectivityProbeOutcome pass(String detail) {
        return new ConnectivityProbeOutcome(Status.PASS, null, detail);
    }

    public static ConnectivityProbeOutcome fail(String errorCode, String detail) {
        return new ConnectivityProbeOutcome(Status.FAIL, errorCode, detail);
    }

    public static ConnectivityProbeOutcome skip(String errorCode, String detail) {
        return new ConnectivityProbeOutcome(Status.SKIP, errorCode, detail);
    }

    public Status getStatus() {
        return status;
    }

    /** Machine-readable error code for FAIL/SKIP; null for PASS. */
    public String getErrorCode() {
        return errorCode;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return status + (errorCode != null ? " (" + errorCode + ")" : "")
                + (detail != null && !detail.isEmpty() ? ": " + detail : "");
    }
}
