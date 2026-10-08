/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorMaturity;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSplitSourceFactory;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;

import java.util.List;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.OBJECT;
import static io.nop.stream.core.connector.registry.ConnectorParamKind.STRING;

/**
 * SPI factory for the {@code message} source type: constructs the FLIP-27
 * {@link MessageSource} (item 19 / P-REQ-28; WI2 plan 2343 Phase 2 moved the factory
 * surface from the legacy {@link MessageSourceFunction}, which keeps its programmatic
 * API only). The message backend is a programmatic OBJECT param
 * ({@code IMessageService}) — the factory is stateless and binds no default backend.
 * Registered in {@code _vfs/nop/stream/beans/connector-message.beans.xml}.
 */
public final class MessageSourceConnectorFactory implements IStreamSplitSourceFactory {

    public static final String TYPE_NAME = "message";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .source(TYPE_NAME, MessageSource.class.getName())
            .sourceConsistency(SourceConsistencyCapability.AT_LEAST_ONCE)
            .parallelism(ConnectorParallelism.PARALLEL)
            .recoverySemantic(ConnectorRecoverySemantic.NONE)
            .params(List.of(
                    ConnectorParamDescriptor.required("topic", STRING,
                            "topic name; parallel sources subscribe per-subtask partition topics"),
                    ConnectorParamDescriptor.required("messageService", OBJECT,
                            "IMessageService backend (Local/Pulsar/Kafka) supplied programmatically"),
                    ConnectorParamDescriptor.optional("valueTypeClass", OBJECT,
                            "optional Class<T> value type for payload decoding")))
            .maturity(ConnectorMaturity.EXPERIMENTAL)
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
    public Source<?, ?, ?> createSource(StreamConnectorConfig config) {
        IMessageService messageService = config.requireObject("messageService", IMessageService.class);
        String topic = config.requireString("topic");
        Class<?> valueTypeClass = config.getObject("valueTypeClass", Class.class);
        if (valueTypeClass != null) {
            return new MessageSource<Object>(messageService, topic, valueTypeClass);
        }
        return new MessageSource<Object>(messageService, topic);
    }
}
