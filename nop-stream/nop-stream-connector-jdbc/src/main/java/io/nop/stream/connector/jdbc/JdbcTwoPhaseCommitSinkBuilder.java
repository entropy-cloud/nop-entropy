/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.jdbc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.nop.dao.jdbc.IJdbcTemplate;

/**
 * Fluent builder for {@link JdbcTwoPhaseCommitSink}.
 *
 * <p>Usage:
 * <pre>{@code
 * JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
 *     .jdbcTemplate(jdbcTemplate)
 *     .querySpace("")
 *     .tableName("orders")
 *     .ledgerTableName("stream_epoch_ledger_v2")
 *     .columns("id", "name", "amount")
 * .build();
 * }</pre>
 */
public class JdbcTwoPhaseCommitSinkBuilder<IN> {

    private IJdbcTemplate jdbcTemplate;
    private String querySpace = "";
    private String tableName;
    private String ledgerTableName;
    private final List<String> columnNames = new ArrayList<>();
    private Function<IN, Map<String, Object>> recordMapper;
    private int maxBatchSize = JdbcTwoPhaseCommitSink.DEFAULT_MAX_BATCH_SIZE;
    private JdbcTwoPhaseCommitSink.DmlMode dmlMode;
    private final List<String> keyColumns = new ArrayList<>();
    private Function<IN, String> opMapper;
    private Function<IN, Map<String, Object>> deleteKeyMapper;

    JdbcTwoPhaseCommitSinkBuilder() {
    }

    public JdbcTwoPhaseCommitSinkBuilder<IN> jdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        return this;
    }

    public JdbcTwoPhaseCommitSinkBuilder<IN> querySpace(String querySpace) {
        this.querySpace = querySpace;
        return this;
    }

    public JdbcTwoPhaseCommitSinkBuilder<IN> tableName(String tableName) {
        this.tableName = tableName;
        return this;
    }

    public JdbcTwoPhaseCommitSinkBuilder<IN> ledgerTableName(String ledgerTableName) {
        this.ledgerTableName = ledgerTableName;
        return this;
    }

    public JdbcTwoPhaseCommitSinkBuilder<IN> columns(String... columns) {
        this.columnNames.addAll(Arrays.asList(columns));
        return this;
    }

    public JdbcTwoPhaseCommitSinkBuilder<IN> addColumn(String columnName) {
        this.columnNames.add(columnName);
        return this;
    }

    public JdbcTwoPhaseCommitSinkBuilder<IN> recordMapper(Function<IN, Map<String, Object>> recordMapper) {
        this.recordMapper = recordMapper;
        return this;
    }

    /**
     * Upper bound for one JDBC batch execution (default
     * {@link JdbcTwoPhaseCommitSink#DEFAULT_MAX_BATCH_SIZE}). Large epochs are
     * committed in segments inside the same transaction.
     */
    public JdbcTwoPhaseCommitSinkBuilder<IN> maxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
        return this;
    }

    /**
     * DML semantics (cdc-design.md §3.5): INSERT（默认，追加）/ UPSERT（键控覆盖写）/
     * UPSERT_DELETE（键控覆盖写 + d 事件删除）。String overload accepts
     * "insert" / "upsert" / "upsert_delete".
     */
    public JdbcTwoPhaseCommitSinkBuilder<IN> dmlMode(String dmlMode) {
        this.dmlMode = JdbcTwoPhaseCommitSink.DmlMode.parse(dmlMode);
        return this;
    }

    public JdbcTwoPhaseCommitSinkBuilder<IN> dmlMode(JdbcTwoPhaseCommitSink.DmlMode dmlMode) {
        this.dmlMode = dmlMode;
        return this;
    }

    /**
     * Key columns for the keyed modes (required for UPSERT/UPSERT_DELETE).
     */
    public JdbcTwoPhaseCommitSinkBuilder<IN> keyColumns(String... keyColumns) {
        this.keyColumns.addAll(Arrays.asList(keyColumns));
        return this;
    }

    /**
     * Operation source for keyed modes: maps a record to its Debezium operation code
     * (c/u/d/r). Optional for {@code io.nop.message.debezium.ChangeEvent} records
     * (read from the envelope); required for other record types.
     */
    public JdbcTwoPhaseCommitSinkBuilder<IN> opMapper(Function<IN, String> opMapper) {
        this.opMapper = opMapper;
        return this;
    }

    /**
     * Key map source for delete events in UPSERT_DELETE mode. Optional for ChangeEvent
     * records (uses {@code getKey()}); mapped records fall back to the value row
     * restricted to {@code keyColumns}.
     */
    public JdbcTwoPhaseCommitSinkBuilder<IN> deleteKeyMapper(Function<IN, Map<String, Object>> deleteKeyMapper) {
        this.deleteKeyMapper = deleteKeyMapper;
        return this;
    }

    public JdbcTwoPhaseCommitSink<IN> build() {
        return new JdbcTwoPhaseCommitSink<>(jdbcTemplate, querySpace, tableName,
                ledgerTableName, columnNames, recordMapper, 0, maxBatchSize, null,
                dmlMode, keyColumns, opMapper, deleteKeyMapper);
    }
}
