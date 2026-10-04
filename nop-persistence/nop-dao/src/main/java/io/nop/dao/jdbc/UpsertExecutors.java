/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.nop.dao.DaoErrors.ARG_QUERY_SPACE;
import static io.nop.dao.DaoErrors.ARG_SQL_TEXT;
import static io.nop.dao.DaoErrors.ERR_DAO_UNKNOWN_QUERY_SPACE;
import static io.nop.dao.DaoErrors.ERR_SQL_FEATURE_NOT_SUPPORTED;
import static io.nop.dao.DaoErrors.ERR_SQL_UNCATEGORIZED_FAILURE;

/**
 * Execution body of the {@link IJdbcTemplate#upsertBatch} default method (WI11 design
 * contract §2.2). Package-private: the primitive's execution face is reached through
 * the template interface; this helper only exists so the logic can carry its own
 * logger without putting one on the core interface.
 *
 * <p>Native path: one REQUIRED transaction, the single upsert statement executed once
 * per row (batch atomicity = transaction atomicity). Fallback path
 * ({@link UpsertPolicy#FALLBACK_INSERT_UPDATE}): per row, INSERT in its own
 * transaction and — when the failure classifies as a duplicate key — UPDATE in a
 * fresh transaction; the split is what keeps the fallback safe on engines that abort
 * the whole transaction on a failed statement.
 */
final class UpsertExecutors {
    static final Logger LOG = LoggerFactory.getLogger(UpsertExecutors.class);

    private UpsertExecutors() {
    }

    static void upsertBatch(IJdbcTemplate tpl, String querySpace, String sqlName, String tableName,
                            List<String> columns, List<String> conflictColumns, List<String> updateColumns,
                            List<Object[]> rows, UpsertPolicy policy) {
        IDialect dialect;
        try {
            dialect = tpl.getDialectForQuerySpace(querySpace);
        } catch (Exception e) {
            // Without a dialect the primitive can neither generate SQL nor execute it
            // (JdbcTemplateImpl.executeUpdate resolves the dialect unconditionally),
            // so resolution failure is a typed error under both policies.
            throw new NopException(ERR_DAO_UNKNOWN_QUERY_SPACE, e).param(ARG_QUERY_SPACE, querySpace);
        }

        validateShape(tableName, querySpace, columns, conflictColumns, updateColumns, rows);

        // The execution face requires a non-empty update set under every policy: the
        // FALLBACK path has no valid UPDATE statement without one, and the key-only
        // form is the SQL-text face's contract (design contract §2.2/§2.4).
        if (updateColumns.isEmpty()) {
            throw new NopException(ERR_SQL_FEATURE_NOT_SUPPORTED)
                    .param(ARG_SQL_TEXT, "upsert execution requires non-empty update columns; the key-only form"
                            + " belongs to the SQL-text face (UpsertStatements.buildNativeUpsertSqlText), table="
                            + tableName + ", querySpace=" + querySpace);
        }

        String upsertText = UpsertStatements.buildNativeUpsertSqlText(dialect, tableName, columns, conflictColumns,
                updateColumns);
        if (upsertText != null) {
            runNativeUpsert(tpl, querySpace, sqlName, upsertText, rows);
            return;
        }

        if (policy == UpsertPolicy.NATIVE_ONLY) {
            throw new NopException(ERR_SQL_FEATURE_NOT_SUPPORTED)
                    .param(ARG_SQL_TEXT, "dialect '" + dialect.getName()
                            + "' has no native upsert (supported: postgresql, mysql/mariadb, h2); policy=NATIVE_ONLY"
                            + ", table=" + tableName + ", querySpace=" + querySpace);
        }

        String insertText = UpsertStatements.buildInsertSqlText(dialect, tableName, columns);
        String updateText = UpsertStatements.buildUpdateSqlText(dialect, tableName, updateColumns, conflictColumns);
        int[] updateIdx = mapIndices(columns, updateColumns);
        int[] conflictIdx = mapIndices(columns, conflictColumns);

        for (Object[] row : rows) {
            try {
                tpl.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
                    tpl.executeUpdate(SQL.begin().name(sqlName).querySpace(querySpace).sql(insertText, row).end());
                    return null;
                });
            } catch (Exception e) {
                if (!UpsertStatements.isDuplicateKeyException(dialect, e)) {
                    throw e instanceof RuntimeException ? (RuntimeException) e
                            : new NopException(ERR_SQL_UNCATEGORIZED_FAILURE, e);
                }
                LOG.debug("{} INSERT failed (duplicate key), running UPDATE in a fresh transaction: table={}",
                        sqlName, tableName, e);
                Object[] updateParams = new Object[updateIdx.length + conflictIdx.length];
                int idx = 0;
                for (int i : updateIdx) {
                    updateParams[idx++] = row[i];
                }
                for (int i : conflictIdx) {
                    updateParams[idx++] = row[i];
                }
                tpl.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
                    tpl.executeUpdate(SQL.begin().name(sqlName + "Update").querySpace(querySpace)
                            .sql(updateText, updateParams).end());
                    return null;
                });
            }
        }
    }

    private static void runNativeUpsert(IJdbcTemplate tpl, String querySpace, String sqlName, String upsertText,
                                        List<Object[]> rows) {
        tpl.txn().runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
            for (Object[] row : rows) {
                tpl.executeUpdate(SQL.begin().name(sqlName).querySpace(querySpace).sql(upsertText, row).end());
            }
            return null;
        });
    }

    private static void validateShape(String tableName, String querySpace, List<String> columns,
                                      List<String> conflictColumns, List<String> updateColumns,
                                      List<Object[]> rows) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("upsert columns must not be empty: table=" + tableName);
        }
        if (conflictColumns.isEmpty()) {
            throw new IllegalArgumentException("upsert conflict keys must not be empty: table=" + tableName);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("upsert rows must not be empty: table=" + tableName);
        }
        for (String col : conflictColumns) {
            if (!columns.contains(col)) {
                throw new IllegalArgumentException("conflict key '" + col + "' is not in the upsert column set"
                        + ": table=" + tableName + ", querySpace=" + querySpace);
            }
        }
        for (String col : updateColumns) {
            if (!columns.contains(col)) {
                throw new IllegalArgumentException("update column '" + col + "' is not in the upsert column set"
                        + ": table=" + tableName + ", querySpace=" + querySpace);
            }
        }
        for (Object[] row : rows) {
            if (row == null || row.length != columns.size()) {
                throw new IllegalArgumentException("upsert row must align with the column set (expected "
                        + columns.size() + " values): table=" + tableName);
            }
        }
    }

    private static int[] mapIndices(List<String> columns, List<String> subset) {
        int[] indices = new int[subset.size()];
        for (int i = 0; i < subset.size(); i++) {
            indices[i] = columns.indexOf(subset.get(i));
        }
        return indices;
    }
}
