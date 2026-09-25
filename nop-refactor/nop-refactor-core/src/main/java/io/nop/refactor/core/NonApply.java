package io.nop.refactor.core;

import java.util.Objects;

/**
 * One edit that was computed but not applied, with its machine-readable
 * reason and context (baseline §四 {@code nonApplied}: "原因枚举 + 上下文").
 * Partial failure in a batch operation is part of the structured result,
 * not an error — but every entry must carry a reason and a locatable
 * context, so construction is fail-closed (no silent, reason-free drops).
 *
 * @param reason why the edit did not apply
 * @param path   the target file the edit belonged to; never blank
 * @param detail context for the AI's retry decision (e.g. the conflicting
 *               edit's source id, or the unresolvable target description);
 *               never blank
 */
public record NonApply(Reason reason, String path, String detail) {

    public enum Reason {
        /** another edit already claimed the same byte range (priority lost). */
        CONFLICT,
        /** the target is outside the operation's declared scope (e.g. an
         *  exempted rule, a cross-module reference out of v1's symbol domain). */
        OUT_OF_SCOPE,
        /** the edit's target symbol could not be resolved. */
        UNRESOLVED_TARGET
    }

    public NonApply {
        Objects.requireNonNull(reason, "reason must not be null (a drop without a reason is a "
                + "silent skip; fail-closed)");
        if (path == null || path.isBlank())
            throw new NopRefactorException("NonApply.path must be a non-blank target path "
                    + "(a drop without a locatable context is a silent skip; fail-closed)");
        if (detail == null || detail.isBlank())
            throw new NopRefactorException("NonApply.detail must be non-blank context for reason "
                    + reason + " at '" + path + "' (fail-closed)");
    }
}
