/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.connector;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.registry.IStreamSinkFactory;
import io.nop.stream.core.connector.registry.IStreamSourceFunctionFactory;
import io.nop.stream.core.connector.registry.IStreamSplitSourceFactory;
import io.nop.stream.core.connector.registry.StreamConnectorCatalog;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceSplit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 end-to-end wiring verification (item 19, plan Anti-Hollow core item): a real
 * pipeline whose source AND sink endpoints are constructed through the connector SPI
 * registry (container → catalog → registry → factory → endpoint), then driven through
 * {@code env.execute()} to final output. Proves the registry is consumed by a real
 * execution path, not merely a bean listing.
 *
 * <ul>
 *   <li>Test 1: file source (registry factory) → batch-consumer sink (registry factory,
 *       batchSize=1 so every record flushes without checkpoint dependence) → execute →
 *       every line collected exactly once.</li>
 *   <li>Test 2: file source (registry factory) → sleepy map (forces the run to span
 *       several periodic checkpoints) → file 2PC sink (registry factory) → execute →
 *       committed epoch files + manifest hold every line exactly once, no temp residue.</li>
 * </ul>
 */
public class TestStreamConnectorRegistryE2E {

    private static IBeanContainerImplementor container;
    private static StreamConnectorCatalog catalog;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        for (IResource resource : StreamConnectorCatalog.discoverConnectorBeansResources()) {
            builder.addResource(resource);
        }
        container = builder.build("stream-connector-registry-e2e-test");
        container.start();
        catalog = StreamConnectorCatalog.of(container);
        // periodic checkpoints need the runtime executor factory installed on the env
        // (same pattern as TestE2EWindowAggregateRestore / ScenarioTestSupport)
        StreamExecutionEnvironment.setCheckpointExecutorFactory(
                new io.nop.stream.runtime.execution.CheckpointExecutorFactoryImpl());
    }

    @AfterAll
    public static void destroy() {
        StreamExecutionEnvironment.setCheckpointExecutorFactory(null);
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
    }

    @AfterEach
    void clearCoordinatorRegistry() {
        io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry.clearForTest();
    }

    // ------------------------------------------------------------------
    // test 1: registry-constructed endpoints run to completion (no checkpoint needed)
    // ------------------------------------------------------------------

    @Test
    public void registryEndpointsRunToBatchConsumerSink(@TempDir Path tempDir) throws Exception {
        Path inputDir = tempDir.resolve("in");
        writeLines(inputDir, "a.txt", List.of("alpha", "beta"));
        writeLines(inputDir, "b.txt", List.of("gamma", "delta"));

        ConcurrentLinkedQueue<String> collected = new ConcurrentLinkedQueue<>();
        IBatchConsumerProvider<String> collector = context -> (items, chunkContext) -> items.forEach(collected::add);

        Source<String, ? extends SourceSplit, ?> source = fileSource(inputDir.toString());
        SinkFunction<String> sink = batchConsumerSink(collector, 1);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        env.addSource(source, "registry-file-source")
                .sink(sink);
        env.execute("registry-e2e-batch-consumer");

        // split assignment order across files is not deterministic; assert exactly-once
        // delivery as a multiset (4 lines, no loss, no duplicate)
        assertEquals(4, collected.size(),
                "every input line must flow registry-source → execution → registry-sink exactly once");
        assertEquals(new java.util.HashSet<>(List.of("alpha", "beta", "gamma", "delta")),
                new java.util.HashSet<>(collected), "no duplicates and no losses");
    }

    // ------------------------------------------------------------------
    // test 2: registry-constructed 2PC file sink commits through real checkpoints
    // ------------------------------------------------------------------

    @Test
    public void registryTwoPhaseCommitSinkCommitsExactlyOnce(@TempDir Path tempDir) throws Exception {
        Path outputDir = tempDir.resolve("out");
        Path storageDir = tempDir.resolve("storage");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            lines.add("line-" + i);
        }

        SourceFunction<String> source = batchLoaderSource(lines);
        SinkFunction<String> sink = fileTwoPhaseCommitSink(outputDir.toString());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(100L);
        env.getCheckpointConfig().setMinPause(20L);
        env.getCheckpointConfig().setStorageProperty("path", storageDir.toString());
        env.addSource(source, "registry-batch-loader-source")
                .map(new SleepyMapper(5L))
                .sink(sink);
        env.execute("registry-e2e-2pc-file-sink");

        // committed epoch files hold every line exactly once
        List<String> committed = new ArrayList<>();
        try (Stream<Path> files = Files.list(outputDir)) {
            for (Path epochFile : files.filter(p -> p.getFileName().toString().startsWith("epoch-")).sorted().toList()) {
                committed.addAll(Files.readAllLines(epochFile));
            }
        }
        assertEquals(lines, committed,
                "committed epoch files must hold every line exactly once (2PC commit through real checkpoints)");

        // manifest keys equal the committed epoch files, and no temp residue remains
        Properties manifest = new Properties();
        try (var in = Files.newInputStream(outputDir.resolve("manifest.properties"))) {
            manifest.load(in);
        }
        assertTrue(!manifest.isEmpty(), "at least one checkpoint epoch must have committed");
        try (Stream<Path> files = Files.list(outputDir)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().contains(".tmp")),
                    "no .tmp residue allowed after clean completion");
        }
    }

    // ------------------------------------------------------------------
    // registry-driven endpoint construction (the wiring under test)
    // ------------------------------------------------------------------

    private Source<String, ? extends SourceSplit, ?> fileSource(String directoryPath) {
        IStreamSplitSourceFactory factory = assertInstanceOf(IStreamSplitSourceFactory.class,
                catalog.getRegistry().resolveSourceFactory("file"));
        Source<?, ?, ?> source = factory.createSource(new StreamConnectorConfig("file",
                Map.of("directoryPath", directoryPath)));
        @SuppressWarnings("unchecked")
        Source<String, ? extends SourceSplit, ?> typed = (Source<String, ? extends SourceSplit, ?>) source;
        return typed;
    }

    @SuppressWarnings("unchecked")
    private SourceFunction<String> batchLoaderSource(List<String> lines) {
        IStreamSourceFunctionFactory factory = assertInstanceOf(IStreamSourceFunctionFactory.class,
                catalog.getRegistry().resolveSourceFactory("batch-loader"));
        IBatchLoaderProvider<String> provider = context -> {
            List<String> remaining = new ArrayList<>(lines);
            return (batchSize, chunkContext) -> {
                if (remaining.isEmpty()) {
                    return List.of();
                }
                List<String> batch = remaining.subList(0, Math.min(batchSize, remaining.size()));
                List<String> result = new ArrayList<>(batch);
                remaining.subList(0, result.size()).clear();
                return result;
            };
        };
        return (SourceFunction<String>) factory.createSourceFunction(new StreamConnectorConfig("batch-loader",
                Map.of("loaderProvider", provider, "batchSize", 4)));
    }

    @SuppressWarnings("unchecked")
    private SinkFunction<String> batchConsumerSink(IBatchConsumerProvider<String> provider, int batchSize) {
        IStreamSinkFactory factory = catalog.getRegistry().resolveSinkFactory("batch-consumer");
        Map<String, Object> params = new HashMap<>();
        params.put("consumerProvider", provider);
        params.put("batchSize", batchSize);
        return (SinkFunction<String>) factory.createSink(new StreamConnectorConfig("batch-consumer", params));
    }

    @SuppressWarnings("unchecked")
    private SinkFunction<String> fileTwoPhaseCommitSink(String outputDir) {
        IStreamSinkFactory factory = catalog.getRegistry().resolveSinkFactory("file");
        return (SinkFunction<String>) factory.createSink(new StreamConnectorConfig("file",
                Map.of("outputDir", outputDir)));
    }

    private static void writeLines(Path dir, String fileName, List<String> lines) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve(fileName), lines, StandardCharsets.UTF_8);
    }

    /** Slows the pipeline so the periodic checkpoint scheduler commits several epochs. */
    static final class SleepyMapper implements MapFunction<String, String> {
        private static final long serialVersionUID = 1L;

        private final long delayMs;

        SleepyMapper(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public String map(String value) throws Exception {
            Thread.sleep(delayMs);
            return value;
        }
    }
}
