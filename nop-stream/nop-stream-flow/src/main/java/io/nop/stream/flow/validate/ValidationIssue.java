/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.validate;

import java.util.Objects;

/**
 * One structured entry of a conf-validate / dry-run report (item 20 / P-REQ-14).
 *
 * <p>Every issue names the layer it came from, the element / endpoint / connector type
 * it is about, the error code, and — whenever the failing contract has one — the
 * option name (attribute, bean name or connector param name) the error refers to.
 * Rendering never interpolates credential plaintext (D4 boundary).
 *
 * <p>Severity: {@link Severity#FAIL} entries fail the run (exit 1); {@link Severity#SKIP}
 * entries are honest "cannot be determined" reports (e.g. an endpoint family without
 * a probe contract) that do NOT fail the run — the D7 exit-0-with-explicit-skips
 * contract.
 */
public final class ValidationIssue {

    public enum Severity {FAIL, SKIP}

    private final int layer;
    private final String target;
    private final String errorCode;
    private final String paramName;
    private final String message;
    private final Severity severity;

    private ValidationIssue(int layer, String target, String errorCode, String paramName,
                            String message, Severity severity) {
        this.layer = layer;
        this.target = Objects.requireNonNull(target);
        this.errorCode = Objects.requireNonNull(errorCode);
        this.paramName = paramName;
        this.message = Objects.requireNonNull(message);
        this.severity = Objects.requireNonNull(severity);
    }

    public static ValidationIssue of(int layer, String target, String errorCode, String paramName,
                                     String message) {
        return new ValidationIssue(layer, target, errorCode, paramName, message, Severity.FAIL);
    }

    /** Explicit skip item (does not fail the run). */
    public static ValidationIssue skip(int layer, String target, String errorCode, String message) {
        return new ValidationIssue(layer, target, errorCode, null, message, Severity.SKIP);
    }

    /** Validation layer: 1 = model parse (stream.xdef), 2 = construction, 3 = connectivity probe. */
    public int getLayer() {
        return layer;
    }

    /** Element id / bean name / connector type name the issue is about. */
    public String getTarget() {
        return target;
    }

    /** Machine-readable error code (e.g. {@code nop.err.stream.bean-not-found}). */
    public String getErrorCode() {
        return errorCode;
    }

    /** Option name (attribute / bean / param name) the error refers to; null when not applicable. */
    public String getParamName() {
        return paramName;
    }

    /** Human-readable message, already free of credential plaintext. */
    public String getMessage() {
        return message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(severity.name()).append("][layer ").append(layer).append("] ")
                .append(target);
        if (paramName != null) {
            sb.append(" option '").append(paramName).append('\'');
        }
        sb.append(": ").append(errorCode).append(" — ").append(message);
        return sb.toString();
    }
}
