/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_DIRECTION_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_DUPLICATE_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_PARAM_REQUIRED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_TYPE_NOT_FOUND;

/**
 * Registry mechanics tests for the connector SPI registry (item 19 / P-REQ-28):
 * enumeration, alias resolution precedence, duplicate rejection, descriptor validation,
 * unknown-type / direction-mismatch fail-fast semantics, and required-param enforcement.
 */
public class TestStreamConnectorRegistry {

    // ------------------------------------------------------------------
    // test factories
    // ------------------------------------------------------------------

    static class TestSourceFactory implements IStreamSourceFunctionFactory {
        @Override
        public String getTypeName() {
            return "demo-source";
        }

        @Override
        public List<String> getAliases() {
            return List.of("demo");
        }

        @Override
        public ConnectorCapabilityDescriptor describeCapabilities() {
            return ConnectorCapabilityDescriptor.source("demo-source", "io.example.DemoSource")
                    .aliases(List.of("demo"))
                    .sourceConsistency(SourceConsistencyCapability.AT_LEAST_ONCE)
                    .parallelism(ConnectorParallelism.PARALLEL)
                    .recoverySemantic(ConnectorRecoverySemantic.NONE)
                    .params(List.of(ConnectorParamDescriptor.required("topic",
                            ConnectorParamKind.STRING, "demo topic")))
                    .build();
        }

        @Override
        public SourceFunction<?> createSourceFunction(StreamConnectorConfig config) {
            config.requireString("topic");
            return new SourceFunction<Object>() {
                @Override
                public void run(SourceContext<Object> ctx) {
                }

                @Override
                public void cancel() {
                }
            };
        }
    }

    static class TestSinkFactory implements IStreamSinkFactory {
        @Override
        public String getTypeName() {
            return "demo-sink";
        }

        @Override
        public ConnectorCapabilityDescriptor describeCapabilities() {
            return ConnectorCapabilityDescriptor.sink("demo-sink", "io.example.DemoSink")
                    .sinkConsistency(SinkConsistencyCapability.AT_LEAST_ONCE)
                    .parallelism(ConnectorParallelism.PARALLEL)
                    .recoverySemantic(ConnectorRecoverySemantic.NONE)
                    .build();
        }

        @Override
        public SinkFunction<?> createSink(StreamConnectorConfig config) {
            return value -> {
            };
        }
    }

    /** Minimal sink used for duplicate/alias/mismatch scenarios. */
    static final class ConflictingSourceFactory extends TestSourceFactory {
        @Override
        public String getTypeName() {
            return "demo"; // collides with the alias of TestSourceFactory
        }

        @Override
        public List<String> getAliases() {
            return List.of();
        }

        @Override
        public ConnectorCapabilityDescriptor describeCapabilities() {
            return ConnectorCapabilityDescriptor.source("demo", "io.example.DemoSource")
                    .sourceConsistency(SourceConsistencyCapability.AT_LEAST_ONCE)
                    .parallelism(ConnectorParallelism.PARALLEL)
                    .recoverySemantic(ConnectorRecoverySemantic.NONE)
                    .build();
        }
    }

    // ------------------------------------------------------------------
    // enumeration + resolution
    // ------------------------------------------------------------------

    @Test
    public void testEnumeratesRegisteredTypesWithCapabilities() {
        StreamConnectorRegistry registry = StreamConnectorRegistry.of(List.of(new TestSourceFactory(),
                new TestSinkFactory()));

        assertEquals(List.of("demo-source"), registry.getRegisteredSourceTypeNames());
        assertEquals(List.of("demo-sink"), registry.getRegisteredSinkTypeNames());

        List<ConnectorCapabilityDescriptor> descriptors = registry.listConnectors();
        assertEquals(2, descriptors.size());
        // SOURCE sorts before SINK (enum natural order), then by type name
        assertEquals("demo-source", descriptors.get(0).getTypeName());
        assertEquals(ConnectorDirection.SOURCE, descriptors.get(0).getDirection());
        assertEquals("demo-sink", descriptors.get(1).getTypeName());
        assertEquals(ConnectorDirection.SINK, descriptors.get(1).getDirection());
    }

    @Test
    public void testResolveByTypeNameAndAliasWithTypeNamePrecedence() {
        TestSourceFactory source = new TestSourceFactory();
        StreamConnectorRegistry registry = StreamConnectorRegistry.of(List.of(source));

        assertEquals(source, registry.resolveSourceFactory("demo-source"));
        assertEquals(source, registry.resolveSourceFactory("demo"));
    }

    @Test
    public void testAliasCollisionIsRejected() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamConnectorRegistry.of(List.of(new TestSourceFactory(),
                        new ConflictingSourceFactory())));
        assertEquals(ERR_STREAM_CONNECTOR_DUPLICATE_TYPE.getErrorCode(), ex.getErrorCode());
        assertEquals("SOURCE", ex.getParam("direction"));
        assertEquals("demo", ex.getParam("typeName"));
        assertNotNull(ex.getParam("existingFactoryClass"));
        assertNotNull(ex.getParam("factoryClass"));
    }

    @Test
    public void testDuplicateTypeNameIsRejected() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamConnectorRegistry.of(List.of(new TestSourceFactory(),
                        new TestSourceFactory())));
        assertEquals(ERR_STREAM_CONNECTOR_DUPLICATE_TYPE.getErrorCode(), ex.getErrorCode());
        assertEquals("demo-source", ex.getParam("typeName"));
    }

    @Test
    public void testSameTypeNameAcrossDirectionsIsAllowed() {
        // direction-scoped namespace: `demo-source` may coexist with a sink of the same name
        TestSinkFactory sameNameSink = new TestSinkFactory() {
            @Override
            public String getTypeName() {
                return "demo-source";
            }

            @Override
            public ConnectorCapabilityDescriptor describeCapabilities() {
                return ConnectorCapabilityDescriptor.sink("demo-source", "io.example.SameNameSink")
                        .sinkConsistency(SinkConsistencyCapability.AT_LEAST_ONCE)
                        .parallelism(ConnectorParallelism.PARALLEL)
                        .recoverySemantic(ConnectorRecoverySemantic.NONE)
                        .build();
            }
        };
        StreamConnectorRegistry registry = StreamConnectorRegistry.of(List.of(new TestSourceFactory(),
                sameNameSink));
        assertEquals(List.of("demo-source"), registry.getRegisteredSourceTypeNames());
        assertEquals(List.of("demo-source"), registry.getRegisteredSinkTypeNames());
    }

    // ------------------------------------------------------------------
    // unknown type / direction mismatch
    // ------------------------------------------------------------------

    @Test
    public void testUnknownSourceTypeFailsFastWithRegisteredList() {
        StreamConnectorRegistry registry = StreamConnectorRegistry.of(List.of(new TestSourceFactory(),
                new TestSinkFactory()));
        StreamException ex = assertThrows(StreamException.class,
                () -> registry.resolveSourceFactory("no-such-type"));
        assertEquals(ERR_STREAM_CONNECTOR_TYPE_NOT_FOUND.getErrorCode(), ex.getErrorCode());
        assertEquals("no-such-type", ex.getParam("typeName"));
        assertEquals("SOURCE", ex.getParam("direction"));
        List<String> registered = assertInstanceOf(List.class, ex.getParam("registeredTypes"));
        assertEquals(List.of("demo-source"), registered);
    }

    @Test
    public void testDirectionMismatchFailsFastWithoutFallingBackToNotFound() {
        StreamConnectorRegistry registry = StreamConnectorRegistry.of(List.of(new TestSourceFactory(),
                new TestSinkFactory()));
        StreamException ex = assertThrows(StreamException.class,
                () -> registry.resolveSourceFactory("demo-sink"));
        assertEquals(ERR_STREAM_CONNECTOR_DIRECTION_MISMATCH.getErrorCode(), ex.getErrorCode());
        assertEquals("demo-sink", ex.getParam("typeName"));
        assertEquals("SOURCE", ex.getParam("expectedDirection"));
        assertEquals("SINK", ex.getParam("actualDirection"));
    }

    // ------------------------------------------------------------------
    // descriptor validation
    // ------------------------------------------------------------------

    @Test
    public void testDescriptorTypeNameMismatchIsRejected() {
        IStreamSinkFactory bad = new TestSinkFactory() {
            @Override
            public ConnectorCapabilityDescriptor describeCapabilities() {
                return ConnectorCapabilityDescriptor.sink("other-name", "io.example.DemoSink")
                        .sinkConsistency(SinkConsistencyCapability.AT_LEAST_ONCE)
                        .parallelism(ConnectorParallelism.PARALLEL)
                        .recoverySemantic(ConnectorRecoverySemantic.NONE)
                        .build();
            }
        };
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamConnectorRegistry.of(List.of(bad)));
        assertEquals(ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("detail")).contains("typeName mismatch"));
    }

    @Test
    public void testDescriptorDirectionMismatchIsRejected() {
        // a sink factory declaring direction SOURCE
        IStreamSinkFactory bad = new TestSinkFactory() {
            @Override
            public ConnectorCapabilityDescriptor describeCapabilities() {
                return ConnectorCapabilityDescriptor.source("demo-sink", "io.example.DemoSink")
                        .sourceConsistency(SourceConsistencyCapability.AT_LEAST_ONCE)
                        .parallelism(ConnectorParallelism.PARALLEL)
                        .recoverySemantic(ConnectorRecoverySemantic.NONE)
                        .build();
            }
        };
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamConnectorRegistry.of(List.of(bad)));
        assertEquals(ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("detail")).contains("SINK"));
    }

    @Test
    public void testDescriptorAliasesMismatchIsRejected() {
        // factory declares alias in getAliases() but the descriptor does not (drift guard)
        IStreamSourceFunctionFactory bad = new TestSourceFactory() {
            @Override
            public List<String> getAliases() {
                return List.of("demo2");
            }
        };
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamConnectorRegistry.of(List.of(bad)));
        assertEquals(ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("detail")).contains("aliases mismatch"));
    }

    // ------------------------------------------------------------------
    // config required params
    // ------------------------------------------------------------------

    @Test
    public void testRequiredParamMissingFailsFast() {
        TestSourceFactory source = new TestSourceFactory();
        StreamConnectorConfig config = new StreamConnectorConfig("demo-source");
        StreamException ex = assertThrows(StreamException.class,
                () -> source.createSourceFunction(config));
        assertEquals(ERR_STREAM_CONNECTOR_PARAM_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("demo-source", ex.getParam("typeName"));
        assertEquals("topic", ex.getParam("paramName"));
        assertEquals("STRING", ex.getParam("paramKind"));
    }

    @Test
    public void testConstructsEndpointViaConfig() {
        TestSourceFactory source = new TestSourceFactory();
        SourceFunction<?> fn = source.createSourceFunction(
                new StreamConnectorConfig("demo-source", java.util.Map.of("topic", "t1")));
        assertNotNull(fn);
    }

    @Test
    public void testRegistryOfEmptyFactoryList() {
        StreamConnectorRegistry registry = StreamConnectorRegistry.of(new ArrayList<>());
        assertEquals(List.of(), registry.getRegisteredSourceTypeNames());
        assertEquals(List.of(), registry.getRegisteredSinkTypeNames());
    }
}
