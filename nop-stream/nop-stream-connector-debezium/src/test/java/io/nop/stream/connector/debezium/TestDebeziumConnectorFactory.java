/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_PARAM_REQUIRED;

/**
 * Factory + capability alignment tests for the {@code debezium-cdc} connector family
 * (item 19 / P-REQ-28).
 */
public class TestDebeziumConnectorFactory {

    @Test
    public void testConstructsCdcSourceWithAlignment() {
        DebeziumCdcSourceConnectorFactory factory = new DebeziumCdcSourceConnectorFactory();
        DebeziumConfig config = new DebeziumConfig();
        config.setName("factory-test");

        DebeziumCdcSourceFunction source = assertInstanceOf(DebeziumCdcSourceFunction.class,
                factory.createSourceFunction(new StreamConnectorConfig("debezium-cdc",
                        Map.of("config", config))));
        assertEquals(SourceConsistencyCapability.REPLAYABLE, source.getSourceConsistency());

        ConnectorCapabilityDescriptor d = factory.describeCapabilities();
        assertEquals("debezium-cdc", d.getTypeName());
        assertEquals(d.getSourceConsistency(), source.getSourceConsistency());
        assertEquals(ConnectorParallelism.SINGLE_INSTANCE, d.getParallelism());
        assertEquals(ConnectorRecoverySemantic.OFFSET_CHECKPOINT, d.getRecoverySemantic());
    }

    @Test
    public void testConfigParamRequired() {
        DebeziumCdcSourceConnectorFactory factory = new DebeziumCdcSourceConnectorFactory();
        StreamException ex = assertThrows(StreamException.class,
                () -> factory.createSourceFunction(new StreamConnectorConfig("debezium-cdc")));
        assertEquals(ERR_STREAM_CONNECTOR_PARAM_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("config", ex.getParam("paramName"));
        assertEquals("OBJECT", ex.getParam("paramKind"));
    }

    @Test
    public void testWrongObjectTypeFailsFast() {
        DebeziumCdcSourceConnectorFactory factory = new DebeziumCdcSourceConnectorFactory();
        assertThrows(StreamException.class, () -> factory.createSourceFunction(
                new StreamConnectorConfig("debezium-cdc", Map.of("config", "not-a-debezium-config"))));
    }
}
