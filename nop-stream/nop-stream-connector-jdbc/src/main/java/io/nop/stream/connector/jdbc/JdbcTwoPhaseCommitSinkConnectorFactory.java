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
                    ConnectorParamDescriptor.optional("columns", STRING_LIST,
                            "ordered list of data column names to write (required for"
                                    + " dmlMode=insert; keyed modes derive the column set from"
                                    + " the first record and fail fast on later drift)"),
                    ConnectorParamDescriptor.optional("recordMapper", OBJECT,
                            "Function<IN,Map<String,Object>> converting records to column values, "
                                    + "supplied programmatically (required for dmlMode=insert; omit"
                                    + " for keyed modes fed with nop ChangeEvent records)"),
                    ConnectorParamDescriptor.optional("dmlMode", STRING,
                            "insert (default) | upsert | upsert_delete (cdc-design.md §3.5)"),
                    ConnectorParamDescriptor.optional("keyColumns", STRING_LIST,
                            "key columns for the keyed modes (required for upsert/upsert_delete)"),
                    ConnectorParamDescriptor.optional("opMapper", OBJECT,
                            "Function<IN,String> mapping a record to its CDC operation code"
                                    + " (c/u/d/r); optional for ChangeEvent records"),
                    ConnectorParamDescriptor.optional("deleteKeyMapper", OBJECT,
                            "Function<IN,Map<String,Object>> building the delete key for"
                                    + " dmlMode=upsert_delete; optional for ChangeEvent records"),
                    ConnectorParamDescriptor.optional("querySpace", STRING,
                            "database/schema identifier, defaults to empty"),
                    ConnectorParamDescriptor.optional("ledgerTableName", STRING,
                            "epoch ledger table name, defaults to stream_epoch_ledger_v2 (v2 adds "
                                    + "the sink_namespace column; the pre-v2 3-column table is "
                                    + "neither migrated nor read)")))
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
        List<String> columns = config.hasParam("columns") ? config.requireStringList("columns") : null;
        Function recordMapper = config.getObject("recordMapper", Function.class);
        String dmlMode = config.getString("dmlMode");
        List<String> keyColumns = config.hasParam("keyColumns") ? config.requireStringList("keyColumns") : null;
        Function opMapper = config.getObject("opMapper", Function.class);
        Function deleteKeyMapper = config.getObject("deleteKeyMapper", Function.class);

        JdbcTwoPhaseCommitSinkBuilder builder = JdbcTwoPhaseCommitSink.builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName(tableName);
        if (columns != null && !columns.isEmpty()) {
            builder.columns(columns.toArray(new String[0]));
        }
        if (recordMapper != null) {
            builder.recordMapper(recordMapper);
        }
        if (dmlMode != null) {
            builder.dmlMode(dmlMode);
        }
        if (keyColumns != null && !keyColumns.isEmpty()) {
            builder.keyColumns(keyColumns.toArray(new String[0]));
        }
        if (opMapper != null) {
            builder.opMapper(opMapper);
        }
        if (deleteKeyMapper != null) {
            builder.deleteKeyMapper(deleteKeyMapper);
        }
        if (querySpace != null) {
            builder.querySpace(querySpace);
        }
        if (ledgerTableName != null) {
            builder.ledgerTableName(ledgerTableName);
        }
        return builder.build();
    }
}
