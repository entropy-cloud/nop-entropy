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
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSourceFunctionFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;

import java.util.List;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.OBJECT;
import static io.nop.stream.core.connector.registry.ConnectorParamKind.STRING;

/**
 * SPI factory for the {@code message} source type: constructs {@link MessageSourceFunction}
 * (item 19 / P-REQ-28). The message backend is a programmatic OBJECT param
 * ({@code IMessageService}) — the factory is stateless and binds no default backend.
 * Registered in {@code _vfs/nop/stream/beans/connector-message.beans.xml}.
 */
public final class MessageSourceConnectorFactory implements IStreamSourceFunctionFactory {

    public static final String TYPE_NAME = "message";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .source(TYPE_NAME, MessageSourceFunction.class.getName())
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
    public SourceFunction<?> createSourceFunction(StreamConnectorConfig config) {
        IMessageService messageService = config.requireObject("messageService", IMessageService.class);
        String topic = config.requireString("topic");
        Class<?> valueTypeClass = config.getObject("valueTypeClass", Class.class);
        if (valueTypeClass != null) {
            return new MessageSourceFunction<>(messageService, topic, (Class) valueTypeClass);
        }
        return new MessageSourceFunction<>(messageService, topic);
    }
}
