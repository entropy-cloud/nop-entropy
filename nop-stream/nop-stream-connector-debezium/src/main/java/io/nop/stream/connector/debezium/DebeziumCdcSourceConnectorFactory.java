/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSourceFunctionFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;

import java.util.List;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.OBJECT;

/**
 * SPI factory for the {@code debezium-cdc} source type: constructs
 * {@link DebeziumCdcSourceFunction} (item 19 / P-REQ-28). The rich Debezium engine
 * configuration is a programmatic OBJECT param ({@code DebeziumConfig}). Registered in
 * {@code _vfs/nop/stream/beans/connector-debezium.beans.xml}.
 */
public final class DebeziumCdcSourceConnectorFactory implements IStreamSourceFunctionFactory {

    public static final String TYPE_NAME = "debezium-cdc";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .source(TYPE_NAME, DebeziumCdcSourceFunction.class.getName())
            .sourceConsistency(SourceConsistencyCapability.REPLAYABLE)
            .parallelism(ConnectorParallelism.SINGLE_INSTANCE)
            .recoverySemantic(ConnectorRecoverySemantic.OFFSET_CHECKPOINT)
            .params(List.of(
                    ConnectorParamDescriptor.required("config", OBJECT,
                            "DebeziumConfig engine configuration (connector name required), "
                                    + "supplied programmatically"),
                    ConnectorParamDescriptor.optional("credentialProvider", OBJECT,
                            "ICredentialProvider for credential:{id}#{field} references on "
                                    + "databaseUser/databasePassword (item 20 / D4; optional — "
                                    + "fail-closed when references exist without it)")))
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
    public SourceFunction<?> createSourceFunction(StreamConnectorConfig config) {
        DebeziumConfig debeziumConfig = config.requireObject("config", DebeziumConfig.class);
        io.nop.credential.api.ICredentialProvider provider =
                config.getObject("credentialProvider", io.nop.credential.api.ICredentialProvider.class);
        if (provider != null) {
            return new DebeziumCdcSourceFunction(debeziumConfig, provider);
        }
        return new DebeziumCdcSourceFunction(debeziumConfig);
    }
}
