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
 *     .ledgerTableName("stream_epoch_ledger")
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

    public JdbcTwoPhaseCommitSink<IN> build() {
        return new JdbcTwoPhaseCommitSink<>(jdbcTemplate, querySpace, tableName,
                ledgerTableName, columnNames, recordMapper);
    }
}
