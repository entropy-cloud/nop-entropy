/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSinkFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;

import java.util.List;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.INT;
import static io.nop.stream.core.connector.registry.ConnectorParamKind.OBJECT;

/**
 * SPI factory for the {@code batch-consumer} sink type: constructs
 * {@link BatchConsumerSinkFunction} bridging nop-batch consumers (item 19 / P-REQ-28).
 * The consumer provider is a programmatic OBJECT param. Registered in
 * {@code _vfs/nop/stream/beans/connector-batch.beans.xml}.
 */
public final class BatchConsumerSinkConnectorFactory implements IStreamSinkFactory {

    public static final String TYPE_NAME = "batch-consumer";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .sink(TYPE_NAME, BatchConsumerSinkFunction.class.getName())
            .sinkConsistency(SinkConsistencyCapability.IDEMPOTENT)
            .parallelism(ConnectorParallelism.PARALLEL)
            .recoverySemantic(ConnectorRecoverySemantic.BUFFERED_RETRY)
            .params(List.of(
                    ConnectorParamDescriptor.required("consumerProvider", OBJECT,
                            "IBatchConsumerProvider<R> supplied programmatically"),
                    ConnectorParamDescriptor.optional("batchSize", INT,
                            "records per flush batch, defaults to 100")))
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
        IBatchConsumerProvider<?> consumerProvider = config.requireObject("consumerProvider",
                IBatchConsumerProvider.class);
        Integer batchSize = config.getInt("batchSize");
        if (batchSize != null) {
            return new BatchConsumerSinkFunction<>(consumerProvider, batchSize);
        }
        return new BatchConsumerSinkFunction<>(consumerProvider);
    }
}
