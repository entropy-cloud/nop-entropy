/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_PARAM_REQUIRED;

/**
 * Factory + capability alignment tests for the nop-batch bridge connector family
 * (item 19 / P-REQ-28).
 */
public class TestBatchConnectorFactories {

    private final IBatchLoaderProvider<String> loaderProvider =
            context -> (batchSize, chunkContext) -> List.of();

    private final IBatchConsumerProvider<String> consumerProvider = context ->
            (items, chunkContext) -> {
            };

    // ------------------------------------------------------------------
    // batch-loader source
    // ------------------------------------------------------------------

    @Test
    public void testBatchLoaderFactoryConstructsWithAlignment() {
        BatchLoaderSourceConnectorFactory factory = new BatchLoaderSourceConnectorFactory();
        BatchLoaderSourceFunction<?> source = assertInstanceOf(BatchLoaderSourceFunction.class,
                factory.createSourceFunction(new StreamConnectorConfig("batch-loader",
                        Map.of("loaderProvider", loaderProvider))));
        assertEquals(SourceConsistencyCapability.AT_LEAST_ONCE, source.getSourceConsistency());

        // optional batchSize threads through the two-arg constructor
        assertInstanceOf(BatchLoaderSourceFunction.class, factory.createSourceFunction(
                new StreamConnectorConfig("batch-loader",
                        Map.of("loaderProvider", loaderProvider, "batchSize", 16))));

        ConnectorCapabilityDescriptor d = factory.describeCapabilities();
        assertEquals("batch-loader", d.getTypeName());
        assertEquals(d.getSourceConsistency(), source.getSourceConsistency());
        assertEquals(ConnectorParallelism.PARALLEL, d.getParallelism());
        assertEquals(ConnectorRecoverySemantic.OFFSET_CHECKPOINT, d.getRecoverySemantic());
    }

    @Test
    public void testBatchLoaderRequiresLoaderProvider() {
        BatchLoaderSourceConnectorFactory factory = new BatchLoaderSourceConnectorFactory();
        StreamException ex = assertThrows(StreamException.class,
                () -> factory.createSourceFunction(new StreamConnectorConfig("batch-loader",
                        Map.of("batchSize", 4))));
        assertEquals(ERR_STREAM_CONNECTOR_PARAM_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("loaderProvider", ex.getParam("paramName"));
    }

    // ------------------------------------------------------------------
    // batch-consumer sink
    // ------------------------------------------------------------------

    @Test
    public void testBatchConsumerFactoryConstructsWithAlignment() {
        BatchConsumerSinkConnectorFactory factory = new BatchConsumerSinkConnectorFactory();
        BatchConsumerSinkFunction<?> sink = assertInstanceOf(BatchConsumerSinkFunction.class,
                factory.createSink(new StreamConnectorConfig("batch-consumer",
                        Map.of("consumerProvider", consumerProvider))));
        assertEquals(SinkConsistencyCapability.IDEMPOTENT, sink.getSinkConsistency());

        assertInstanceOf(BatchConsumerSinkFunction.class, factory.createSink(
                new StreamConnectorConfig("batch-consumer",
                        Map.of("consumerProvider", consumerProvider, "batchSize", 8))));

        ConnectorCapabilityDescriptor d = factory.describeCapabilities();
        assertEquals("batch-consumer", d.getTypeName());
        assertEquals(d.getSinkConsistency(), sink.getSinkConsistency());
        assertEquals(ConnectorParallelism.PARALLEL, d.getParallelism());
        assertEquals(ConnectorRecoverySemantic.BUFFERED_RETRY, d.getRecoverySemantic());
    }

    @Test
    public void testBatchConsumerRequiresConsumerProvider() {
        BatchConsumerSinkConnectorFactory factory = new BatchConsumerSinkConnectorFactory();
        StreamException ex = assertThrows(StreamException.class,
                () -> factory.createSink(new StreamConnectorConfig("batch-consumer")));
        assertEquals(ERR_STREAM_CONNECTOR_PARAM_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("consumerProvider", ex.getParam("paramName"));
    }
}
