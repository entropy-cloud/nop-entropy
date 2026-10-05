/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

/**
 * Explicit behavior of the DAO batch-upsert primitive when the resolved dialect has
 * no native single-statement upsert (WI11 design contract §2.2). The policy is a
 * required argument with no default: callers must state the unsupported-dialect
 * behavior at the call site, so an unsupported dialect can never be handled silently.
 */
public enum UpsertPolicy {
    /**
     * Fail loudly with {@code nop.err.dao.sql.feature-not-supported} when the dialect
     * has no native upsert. Mirrors the JdbcTwoPhaseCommitSink behavior (built-in sync
     * scope rejects unsupported dialects for keyed writes).
     */
    NATIVE_ONLY,

    /**
     * Fall back to an INSERT in its own transaction, then — when the failure is
     * classified as a duplicate key by {@link UpsertStatements#isDuplicateKeyException} —
     * an UPDATE in a fresh transaction, per row. Mirrors the JdbcCheckpointStorage
     * behavior: the two-transaction split is what keeps the fallback safe on engines
     * that abort the whole transaction on a failed statement (e.g. PostgreSQL).
     * Batch guarantee is per-row atomicity only (see design contract §4.3).
     */
    FALLBACK_INSERT_UPDATE
}
