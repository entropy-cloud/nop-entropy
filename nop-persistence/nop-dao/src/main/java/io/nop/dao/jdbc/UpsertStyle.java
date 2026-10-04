/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

/**
 * Native single-statement upsert styles supported by the DAO batch-upsert
 * primitive (WI11). A dialect maps to a style when its {@link io.nop.dao.dialect.IDialect#getName()
 * name} matches one of the family prefixes resolved by
 * {@link UpsertStatements#resolveUpsertStyle(io.nop.dao.dialect.IDialect)}; dialects
 * outside the three families have no native style and callers must handle the
 * unsupported case explicitly (never silently).
 */
public enum UpsertStyle {
    /**
     * PostgreSQL family: {@code INSERT ... ON CONFLICT (conflictCols) DO UPDATE SET
     * col = EXCLUDED.col} (key-only form: {@code DO NOTHING}).
     */
    POSTGRESQL,

    /**
     * MySQL / MariaDB family: {@code INSERT ... ON DUPLICATE KEY UPDATE col = VALUES(col)}
     * (key-only form: first conflict key self-assigned to a no-op).
     */
    MYSQL,

    /**
     * H2: {@code MERGE INTO t (cols) KEY (conflictCols) VALUES (...)} — natively
     * key-only capable. Chosen over H2's PostgreSQL-style {@code ON CONFLICT} because
     * H2 2.3.232 raises a syntax error on the {@code DO UPDATE SET col = EXCLUDED.col}
     * form (known pitfall recorded in TestJdbcCheckpointStorage) while MERGE..KEY has
     * green real-execution precedent on the H2 version resolved by this repository
     * (2.4.240).
     */
    H2
}
