/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.jdbc;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSinkFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;

import java.util.List;
import java.util.function.Function;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.OBJECT;
import static io.nop.stream.core.connector.registry.ConnectorParamKind.STRING;
import static io.nop.stream.core.connector.registry.ConnectorParamKind.STRING_LIST;

/**
 * SPI factory for the {@code jdbc-2pc} sink type: constructs the exactly-once
 * {@link JdbcTwoPhaseCommitSink} via its fluent builder (item 19 / P-REQ-28). The JDBC
 * template and the record-mapper function are programmatic OBJECT params. Registered in
 * {@code _vfs/nop/stream/beans/connector-jdbc.beans.xml}.
 */
public final class JdbcTwoPhaseCommitSinkConnectorFactory implements IStreamSinkFactory {

    public static final String TYPE_NAME = "jdbc-2pc";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .sink(TYPE_NAME, JdbcTwoPhaseCommitSink.class.getName())
            .sinkConsistency(SinkConsistencyCapability.TWO_PHASE_COMMIT)
            .parallelism(ConnectorParallelism.PARALLEL)
            .recoverySemantic(ConnectorRecoverySemantic.TWO_PHASE_PENDING_COMMITS)
            .params(List.of(
                    ConnectorParamDescriptor.required("jdbcTemplate", OBJECT,
                            "IJdbcTemplate for the target database, supplied programmatically"),
                    ConnectorParamDescriptor.required("tableName", STRING,
                            "target data table name"),
                    ConnectorParamDescriptor.required("columns", STRING_LIST,
                            "ordered list of data column names to write"),
                    ConnectorParamDescriptor.required("recordMapper", OBJECT,
                            "Function<IN,Map<String,Object>> converting records to column values, "
                                    + "supplied programmatically"),
                    ConnectorParamDescriptor.optional("querySpace", STRING,
                            "database/schema identifier, defaults to empty"),
                    ConnectorParamDescriptor.optional("ledgerTableName", STRING,
                            "epoch ledger table name, defaults to stream_epoch_ledger")))
            .build();

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public ConnectorCapabilityDescriptor describeCapabilities() {
        return DESCRIPTOR;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SinkFunction<?> createSink(StreamConnectorConfig config) {
        IJdbcTemplate jdbcTemplate = config.requireObject("jdbcTemplate", IJdbcTemplate.class);
        String querySpace = config.getString("querySpace");
        String tableName = config.requireString("tableName");
        String ledgerTableName = config.getString("ledgerTableName");
        List<String> columns = config.requireStringList("columns");
        Function recordMapper = config.requireObject("recordMapper", Function.class);

        JdbcTwoPhaseCommitSinkBuilder builder = JdbcTwoPhaseCommitSink.builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName(tableName)
                .columns(columns.toArray(new String[0]))
                .recordMapper(recordMapper);
        if (querySpace != null) {
            builder.querySpace(querySpace);
        }
        if (ledgerTableName != null) {
            builder.ledgerTableName(ledgerTableName);
        }
        return builder.build();
    }
}
