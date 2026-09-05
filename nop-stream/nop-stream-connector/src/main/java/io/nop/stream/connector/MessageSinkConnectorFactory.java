/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSinkFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;

import java.util.List;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.OBJECT;
import static io.nop.stream.core.connector.registry.ConnectorParamKind.STRING;

/**
 * SPI factory for the {@code message} sink type: constructs {@link MessageSinkFunction}
 * (item 19 / P-REQ-28). Registered in {@code _vfs/nop/stream/beans/connector-message.beans.xml}.
 */
public final class MessageSinkConnectorFactory implements IStreamSinkFactory {

    public static final String TYPE_NAME = "message";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .sink(TYPE_NAME, MessageSinkFunction.class.getName())
            .sinkConsistency(SinkConsistencyCapability.AT_LEAST_ONCE)
            .parallelism(ConnectorParallelism.PARALLEL)
            .recoverySemantic(ConnectorRecoverySemantic.NONE)
            .params(List.of(
                    ConnectorParamDescriptor.required("topic", STRING,
                            "topic name to send records to"),
                    ConnectorParamDescriptor.required("messageService", OBJECT,
                            "IMessageService backend (Local/Pulsar/Kafka) supplied programmatically")))
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
    public SinkFunction<?> createSink(StreamConnectorConfig config) {
        IMessageService messageService = config.requireObject("messageService", IMessageService.class);
        String topic = config.requireString("topic");
        return new MessageSinkFunction<>(messageService, topic);
    }
}
