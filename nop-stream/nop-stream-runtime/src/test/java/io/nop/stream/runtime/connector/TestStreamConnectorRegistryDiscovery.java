/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.connector;

import io.nop.api.core.message.IMessageService;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.message.core.local.LocalMessageService;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorDirection;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.StreamConnectorCatalog;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.connector.registry.StreamConnectorRegistry;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_DIRECTION_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_TYPE_NOT_FOUND;

/**
 * Aggregate registration discovery test (item 19 / P-REQ-28 acceptance): builds a NopIoC
 * container from the {@code connector-*.beans.xml} files discovered on the classpath,
 * aggregates all factory beans into the registry via the {@link StreamConnectorCatalog}
 * tool entry, and asserts the full registered set, per-type capability declarations
 * (aligned with the capability matrix), registry→resolution→construction for all eight
 * endpoint components, and unknown-type / direction-mismatch fail-fast semantics.
 *
 * <p>This test is also the Phase 1 §8.4 alignment record: the capability values asserted
 * here are the single-fact-source code anchor for
 * {@code docs-for-ai/03-modules/nop-stream-connectors.md}.
 */
public class TestStreamConnectorRegistryDiscovery {

    private static IBeanContainerImplementor container;
    private static StreamConnectorCatalog catalog;
    private static List<IResource> discoveredBeansFiles;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        discoveredBeansFiles = StreamConnectorCatalog.discoverConnectorBeansResources();

        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        for (IResource resource : discoveredBeansFiles) {
            builder.addResource(resource);
        }
        container = builder.build("stream-connector-registry-discovery-test");
        container.start();
        catalog = StreamConnectorCatalog.of(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // beans file discovery
    // ------------------------------------------------------------------

    @Test
    public void testDiscoversAllFiveConnectorBeansFiles() {
        List<String> names = discoveredBeansFiles.stream().map(IResource::getName).sorted().toList();
        assertEquals(List.of("connector-batch.beans.xml", "connector-debezium.beans.xml",
                "connector-file.beans.xml", "connector-jdbc.beans.xml", "connector-message.beans.xml"), names);
    }

    // ------------------------------------------------------------------
    // full registered set (P-REQ-28 acceptance)
    // ------------------------------------------------------------------

    @Test
    public void testRegistryEnumeratesAllEightEndpointComponents() {
        StreamConnectorRegistry registry = catalog.getRegistry();
        assertEquals(List.of("batch-loader", "debezium-cdc", "file", "message"),
                registry.getRegisteredSourceTypeNames());
        assertEquals(List.of("batch-consumer", "file", "jdbc-2pc", "message"),
                registry.getRegisteredSinkTypeNames());
        assertEquals(8, catalog.listConnectors().size());
    }

    @Test
    public void testAllEightBuiltInFactoriesDeclareNoAliases() {
        for (ConnectorCapabilityDescriptor d : catalog.listConnectors()) {
            assertEquals(List.of(), d.getAliases(), d.getTypeName() + " must declare no aliases");
        }
    }

    // ------------------------------------------------------------------
    // per-type capability matrix assertions (§8.4 alignment record)
    // ------------------------------------------------------------------

    @Test
    public void testCapabilityMatrixAlignedWithDesignAdjudication() {
        assertDescriptor(ConnectorDirection.SOURCE, "file",
                "io.nop.stream.connector.file.FileSource",
                "AT_LEAST_ONCE", ConnectorParallelism.PARALLEL,
                ConnectorRecoverySemantic.SPLIT_CURSOR_CHECKPOINT, "directoryPath");
        assertDescriptor(ConnectorDirection.SOURCE, "message",
                "io.nop.stream.connector.MessageSourceFunction",
                "AT_LEAST_ONCE", ConnectorParallelism.PARALLEL,
                ConnectorRecoverySemantic.NONE, "topic", "messageService");
        assertDescriptor(ConnectorDirection.SOURCE, "debezium-cdc",
                "io.nop.stream.connector.debezium.DebeziumCdcSourceFunction",
                "REPLAYABLE", ConnectorParallelism.SINGLE_INSTANCE,
                ConnectorRecoverySemantic.OFFSET_CHECKPOINT, "config");
        assertDescriptor(ConnectorDirection.SOURCE, "batch-loader",
                "io.nop.stream.connector.batch.BatchLoaderSourceFunction",
                "AT_LEAST_ONCE", ConnectorParallelism.PARALLEL,
                ConnectorRecoverySemantic.OFFSET_CHECKPOINT, "loaderProvider");
        assertDescriptor(ConnectorDirection.SINK, "file",
                "io.nop.stream.connector.file.FileTwoPhaseCommitSink",
                "TWO_PHASE_COMMIT", ConnectorParallelism.PLANNING_GATE_PARALLELISM_1,
                ConnectorRecoverySemantic.TWO_PHASE_PENDING_COMMITS, "outputDir");
        assertDescriptor(ConnectorDirection.SINK, "message",
                "io.nop.stream.connector.MessageSinkFunction",
                "AT_LEAST_ONCE", ConnectorParallelism.PARALLEL,
                ConnectorRecoverySemantic.NONE, "topic", "messageService");
        assertDescriptor(ConnectorDirection.SINK, "jdbc-2pc",
                "io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink",
                "TWO_PHASE_COMMIT", ConnectorParallelism.PLANNING_GATE_PARALLELISM_1,
                ConnectorRecoverySemantic.TWO_PHASE_PENDING_COMMITS,
                "jdbcTemplate", "tableName", "columns", "recordMapper");
        assertDescriptor(ConnectorDirection.SINK, "batch-consumer",
                "io.nop.stream.connector.batch.BatchConsumerSinkFunction",
                "IDEMPOTENT", ConnectorParallelism.PARALLEL,
                ConnectorRecoverySemantic.BUFFERED_RETRY, "consumerProvider");
    }

    private void assertDescriptor(ConnectorDirection direction, String typeName, String componentClass,
                                  String consistency, ConnectorParallelism parallelism,
                                  ConnectorRecoverySemantic recovery, String... requiredParams) {
        ConnectorCapabilityDescriptor d = find(direction, typeName);
        assertEquals(componentClass, d.getComponentClass(), typeName);
        assertEquals(consistency, d.consistencyValue(), typeName);
        assertEquals(parallelism, d.getParallelism(), typeName);
        assertEquals(recovery, d.getRecoverySemantic(), typeName);
        List<String> actual = d.getParams().stream()
                .filter(p -> p.isRequired()).map(p -> p.getName()).sorted().toList();
        assertEquals(List.of(requiredParams).stream().sorted().toList(), actual, typeName);
    }

    private ConnectorCapabilityDescriptor find(ConnectorDirection direction, String typeName) {
        for (ConnectorCapabilityDescriptor d : catalog.listConnectors()) {
            if (d.getDirection() == direction && d.getTypeName().equals(typeName)) {
                return d;
            }
        }
        throw new AssertionError("descriptor not found: " + direction + " " + typeName);
    }

    // ------------------------------------------------------------------
    // registry → resolution → construction for all eight components
    // ------------------------------------------------------------------

    @Test
    public void testCatalogProbesAllEightEndpoints(@TempDir Path tempDir) {
        IMessageService messageService = new LocalMessageService();

        // split sources have no instance-level consistency accessor (§8.1): descriptor
        // pinned to the matrix + behavior tests; probe reports alignment=false for them
        assertProbe(catalog.probe(ConnectorDirection.SOURCE, "file",
                config("file", Map.of("directoryPath", tempDir.toString()))), false);
        assertProbe(catalog.probe(ConnectorDirection.SOURCE, "message",
                config("message", Map.of("topic", "t", "messageService", messageService))), true);
        DebeziumConfig debeziumConfig = new DebeziumConfig();
        debeziumConfig.setName("discovery-probe");
        assertProbe(catalog.probe(ConnectorDirection.SOURCE, "debezium-cdc",
                config("debezium-cdc", Map.of("config", debeziumConfig))), true);
        assertProbe(catalog.probe(ConnectorDirection.SOURCE, "batch-loader",
                config("batch-loader", Map.of("loaderProvider", loaderProvider()))), true);

        assertProbe(catalog.probe(ConnectorDirection.SINK, "file",
                config("file", Map.of("outputDir", tempDir.resolve("out").toString()))), true);
        assertProbe(catalog.probe(ConnectorDirection.SINK, "message",
                config("message", Map.of("topic", "t", "messageService", messageService))), true);
        assertProbe(catalog.probe(ConnectorDirection.SINK, "jdbc-2pc",
                config("jdbc-2pc", jdbcParams())), true);
        assertProbe(catalog.probe(ConnectorDirection.SINK, "batch-consumer",
                config("batch-consumer", Map.of("consumerProvider", consumerProvider()))), true);
    }

    private void assertProbe(io.nop.stream.core.connector.registry.ConnectorProbeResult result,
                             boolean expectInstanceAligned) {
        assertEquals(expectInstanceAligned, result.isInstanceConsistencyAligned(), result.getTypeName());
        assertTrue(result.getEndpointClass().startsWith("io.nop.stream.connector"),
                result.getEndpointClass());
    }

    private StreamConnectorConfig config(String typeName, Map<String, Object> params) {
        return new StreamConnectorConfig(typeName, params);
    }

    private Map<String, Object> jdbcParams() {
        // construction-only proxy: the sink constructor only null-checks the template and
        // the probe never invokes it (commit paths are covered by connector-jdbc module tests)
        IJdbcTemplate template = (IJdbcTemplate) Proxy.newProxyInstance(
                IJdbcTemplate.class.getClassLoader(), new Class<?>[]{IJdbcTemplate.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(
                            "construction-only template must not be invoked: " + method);
                });
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("jdbcTemplate", template);
        params.put("tableName", "target_data");
        params.put("columns", List.of("id", "name"));
        params.put("recordMapper", (java.util.function.Function<Map<String, Object>, Map<String, Object>>) row -> row);
        return params;
    }

    private io.nop.batch.core.IBatchLoaderProvider<String> loaderProvider() {
        return context -> (batchSize, chunkContext) -> List.of();
    }

    private io.nop.batch.core.IBatchConsumerProvider<String> consumerProvider() {
        return context -> (items, chunkContext) -> {
        };
    }

    // ------------------------------------------------------------------
    // fail-fast semantics through the aggregated registry
    // ------------------------------------------------------------------

    @Test
    public void testUnknownTypeThroughCatalogFailsFastWithFullRegisteredList() {
        StreamException ex = assertThrows(StreamException.class,
                () -> catalog.probe(ConnectorDirection.SINK, "clickhouse",
                        config("clickhouse", Map.of())));
        assertEquals(ERR_STREAM_CONNECTOR_TYPE_NOT_FOUND.getErrorCode(), ex.getErrorCode());
        assertEquals("clickhouse", ex.getParam("typeName"));
        assertEquals("SINK", ex.getParam("direction"));
        assertEquals(List.of("batch-consumer", "file", "jdbc-2pc", "message"), ex.getParam("registeredTypes"));
    }

    @Test
    public void testDirectionMismatchThroughCatalogFailsFast() {
        StreamException ex = assertThrows(StreamException.class,
                () -> catalog.getRegistry().resolveSourceFactory("jdbc-2pc"));
        assertEquals(ERR_STREAM_CONNECTOR_DIRECTION_MISMATCH.getErrorCode(), ex.getErrorCode());
        assertEquals("SOURCE", ex.getParam("expectedDirection"));
        assertEquals("SINK", ex.getParam("actualDirection"));
    }

    @Test
    public void testCatalogRendersListingWithAllEightConnectors() {
        String listing = catalog.renderListing();
        assertTrue(listing.contains("[SOURCE] file"), listing);
        assertTrue(listing.contains("[SOURCE] debezium-cdc"), listing);
        assertTrue(listing.contains("[SINK] jdbc-2pc"), listing);
        assertTrue(listing.contains("delivery=TWO_PHASE_COMMIT"), listing);
        assertTrue(listing.contains("parallelism=PLANNING_GATE_PARALLELISM_1"), listing);
    }
}
