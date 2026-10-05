/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.exception.ISQLExceptionTranslator;
import io.nop.dao.exceptions.JdbcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.StringJoiner;

import static io.nop.dao.DaoErrors.ERR_SQL_DUPLICATE_KEY;

/**
 * Static face of the DAO batch-upsert primitive (WI11 design contract §2.3): native
 * upsert style resolution, dialect-specific upsert SQL text generation, and the
 * unified duplicate-key exception predicate. Stateless and thread-safe.
 *
 * <p>This class is the single implementation of the upsert dialect branching that used
 * to be duplicated as private enums in nop-stream's {@code JdbcCheckpointStorage} and
 * {@code JdbcTwoPhaseCommitSink}. All generated identifiers go through
 * {@link IDialect#escapeSQLName(String)} — simple names pass through unchanged
 * ({@code DialectImpl.escapeSQLName} only quotes reserved keywords and invalid
 * variable names), so escaping is behavior-neutral for regular schemas and a net fix
 * for reserved-word identifiers.
 *
 * <p>Duplicate-key detection is the unified three-stage rule (design contract §3.2):
 * translator first (exact vendorCode/SQLState normalization from the dialect's
 * {@code <errorCodes>} registration), then an unconditional SQLState-23505 fallback
 * (covers dialects without a registration — this is what keeps the oracle-model +
 * H2 collision path of TestJdbcCheckpointStorage green), then the SQLState-23000 +
 * known-vendor-code carryover band from the legacy decision table (MySQL 1022 is
 * explicitly preserved; the registered codes 1062/2627/2601/1 also make stage 3
 * robust when a dialect model lacks {@code <errorCodes>} configuration).
 */
public final class UpsertStatements {
    static final Logger LOG = LoggerFactory.getLogger(UpsertStatements.class);

    /** SQLState for unique-constraint violations (PostgreSQL / H2 / Derby family). */
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";
    /** Generic integrity-violation SQLState reported by MySQL / SQL Server / Oracle. */
    private static final String SQLSTATE_INTEGRITY_VIOLATION = "23000";

    private UpsertStatements() {
    }

    /**
     * Resolve the native upsert style of a dialect by its name family prefix
     * (case-insensitive): {@code postgresql}/{@code pg} → POSTGRESQL,
     * {@code mysql}/{@code mariadb} → MYSQL, {@code h2} → H2.
     *
     * @return the resolved style, or {@code null} when the dialect (or its name)
     *         resolves to no supported native upsert. The null return is an explicit
     *         contract signal — callers must branch on it (execute per policy, or
     *         raise their own typed error); they must not ignore it.
     */
    public static UpsertStyle resolveUpsertStyle(IDialect dialect) {
        if (dialect == null) {
            return null;
        }
        String name = dialect.getName() == null ? "" : dialect.getName().toLowerCase();
        if (name.startsWith("postgresql") || name.startsWith("pg")) {
            return UpsertStyle.POSTGRESQL;
        }
        if (name.startsWith("mysql") || name.startsWith("mariadb")) {
            return UpsertStyle.MYSQL;
        }
        if (name.startsWith("h2")) {
            return UpsertStyle.H2;
        }
        return null;
    }

    /**
     * Build the dialect's native single-statement upsert text. Table and column
     * identifiers are escaped with {@link IDialect#escapeSQLName(String)}. When
     * {@code updateColumns} is empty the key-only form is generated (POSTGRESQL:
     * {@code DO NOTHING}; MYSQL: first conflict key self-assigned; H2: MERGE is
     * naturally key-only).
     *
     * @return the upsert SQL text, or {@code null} when the dialect has no native
     *         upsert style (explicit contract signal, see {@link #resolveUpsertStyle}).
     */
    public static String buildNativeUpsertSqlText(IDialect dialect, String tableName,
                                                  List<String> columns, List<String> conflictColumns,
                                                  List<String> updateColumns) {
        UpsertStyle style = resolveUpsertStyle(dialect);
        if (style == null) {
            return null;
        }
        String insert = "INSERT INTO " + dialect.escapeSQLName(tableName)
                + " (" + escapeJoin(dialect, columns) + ") VALUES (" + placeholders(columns.size()) + ")";

        switch (style) {
            case POSTGRESQL: {
                if (updateColumns.isEmpty()) {
                    return insert + " ON CONFLICT (" + escapeJoin(dialect, conflictColumns) + ") DO NOTHING";
                }
                StringJoiner set = new StringJoiner(", ");
                for (String col : updateColumns) {
                    String escaped = dialect.escapeSQLName(col);
                    set.add(escaped + " = EXCLUDED." + escaped);
                }
                return insert + " ON CONFLICT (" + escapeJoin(dialect, conflictColumns) + ") DO UPDATE SET " + set;
            }
            case MYSQL: {
                if (updateColumns.isEmpty()) {
                    String firstKey = dialect.escapeSQLName(conflictColumns.get(0));
                    return insert + " ON DUPLICATE KEY UPDATE " + firstKey + " = " + firstKey;
                }
                StringJoiner set = new StringJoiner(", ");
                for (String col : updateColumns) {
                    String escaped = dialect.escapeSQLName(col);
                    set.add(escaped + " = VALUES(" + escaped + ")");
                }
                return insert + " ON DUPLICATE KEY UPDATE " + set;
            }
            case H2:
                return "MERGE INTO " + dialect.escapeSQLName(tableName)
                        + " (" + escapeJoin(dialect, columns) + ") KEY (" + escapeJoin(dialect, conflictColumns)
                        + ") VALUES (" + placeholders(columns.size()) + ")";
            default:
                // resolveUpsertStyle only returns the three styles above.
                throw new IllegalStateException("unreachable upsert style: " + style);
        }
    }

    /**
     * Unified duplicate-key classification (design contract §3.2): for every
     * {@link SQLException} reachable through the cause chain and the JDBC
     * {@code getNextException()} chain — 1) the dialect's exception translator is
     * consulted first and a {@code ERR_SQL_DUPLICATE_KEY} verdict decides; 2) an
     * unconditional SQLState {@code 23505} fallback decides; 3) SQLState {@code 23000}
     * with a vendor code known to denote duplicate keys decides. Anything else is not
     * a duplicate — the caller rethrows (false negatives fail loudly, the safe
     * direction; false positives would mask real errors behind an UPDATE retry).
     *
     * @param dialect the dialect in whose error-code registration the exception is
     *                interpreted; may be {@code null}, in which case only the
     *                dialect-independent stages 2 and 3 apply
     * @return {@code true} only when the exception chain contains a duplicate-key
     *         violation
     */
    public static boolean isDuplicateKeyException(IDialect dialect, Throwable e) {
        ISQLExceptionTranslator translator = dialect == null ? null : dialect.getSQLExceptionTranslator();
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException) {
                SQLException sqlEx = (SQLException) cause;
                if (isDuplicateSQLException(translator, sqlEx)) {
                    return true;
                }
                // SQLException chains batch failures via getNextException()
                for (SQLException next = sqlEx.getNextException(); next != null; next = next.getNextException()) {
                    if (isDuplicateSQLException(translator, next)) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Plain INSERT text for the {@link UpsertPolicy#FALLBACK_INSERT_UPDATE} path
     * (escaped identifiers, one {@code ?} placeholder per column, values aligned
     * positionally with {@code columns}).
     */
    static String buildInsertSqlText(IDialect dialect, String tableName, List<String> columns) {
        return "INSERT INTO " + dialect.escapeSQLName(tableName)
                + " (" + escapeJoin(dialect, columns) + ") VALUES (" + placeholders(columns.size()) + ")";
    }

    /**
     * UPDATE-by-conflict-key text for the fallback path: {@code SET updateCol = ?}
     * for each update column, then {@code WHERE keyCol = ?} for each conflict key
     * (parameter order: update column values first, then conflict key values).
     */
    static String buildUpdateSqlText(IDialect dialect, String tableName,
                                     List<String> updateColumns, List<String> conflictColumns) {
        StringJoiner set = new StringJoiner(", ");
        for (String col : updateColumns) {
            set.add(dialect.escapeSQLName(col) + " = ?");
        }
        StringJoiner where = new StringJoiner(" AND ");
        for (String col : conflictColumns) {
            where.add(dialect.escapeSQLName(col) + " = ?");
        }
        return "UPDATE " + dialect.escapeSQLName(tableName) + " SET " + set + " WHERE " + where;
    }

    private static boolean isDuplicateSQLException(ISQLExceptionTranslator translator, SQLException ex) {
        // Stage 1 — translator first: the dialect's <errorCodes> registration yields
        // the exact normalization (e.g. H2 23001, DB2 -803, DM -6013/-6015, TDengine
        // 1062). translate() never returns null and only produces
        // ERR_SQL_DUPLICATE_KEY from a registered vendor code / SQLState match, so
        // its type-based branches cannot interfere with the stages below.
        if (translator != null) {
            JdbcException translated = translator.translate("upsert-duplicate-key-check", ex);
            if (ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(translated.getErrorCode())) {
                return true;
            }
        }
        // Stage 2 — unconditional SQLState-23505 fallback: covers dialects whose
        // vendor code / SQLState is not registered (e.g. the oracle dialect model
        // driven against an H2 backend in the GENERIC-fallback collision test).
        String sqlState = ex.getSQLState();
        if (SQLSTATE_UNIQUE_VIOLATION.equals(sqlState)) {
            return true;
        }
        // Stage 3 — SQLState-23000 carryover band from the legacy decision table:
        // MySQL 1062 (duplicate entry) / 1022 (duplicate key), SQL Server 2627/2601,
        // Oracle 1 (ORA-00001). Keeps classification intact when the dialect model
        // has no <errorCodes> registration at all.
        return SQLSTATE_INTEGRITY_VIOLATION.equals(sqlState) && isKnownDuplicateVendorCode(ex.getErrorCode());
    }

    private static boolean isKnownDuplicateVendorCode(int vendorCode) {
        return vendorCode == 1062 || vendorCode == 1022
                || vendorCode == 2627 || vendorCode == 2601
                || vendorCode == 1;
    }

    private static String escapeJoin(IDialect dialect, List<String> names) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String name : names) {
            joiner.add(dialect.escapeSQLName(name));
        }
        return joiner.toString();
    }

    private static String placeholders(int count) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < count; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }
}
