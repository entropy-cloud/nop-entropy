/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.ClusterPipelineFactory;
import io.nop.stream.runtime.rpc.RemotePipelineSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 14 (composite-scenario distributed): ungated serialization + determinism proof
 * for the distributed scenario deployment contract. The {@code deployTask} RPC
 * Java-serializes the {@code TaskDeploymentDescriptor}; for XDSL pipelines the
 * descriptor carries a {@link RemotePipelineSpec} (declaration + serializable beans)
 * and every TaskManager rebuilds the graph locally. This test pins the two invariants
 * the cross-JVM deployment depends on, WITHOUT spawning JVMs (Rule #25):
 *
 * <ol>
 *   <li><b>Spec serializability</b>: the bean set of every scenario variant survives
 *       the Java round trip (a non-serializable bean would fail the deployTask RPC
 *       at runtime only).</li>
 *   <li><b>Rebuild determinism</b>: two independent rebuilds from the same spec (as
 *       the coordinator and each TaskManager perform them) produce IDENTICAL vertex
 *       id sets and StreamModel fingerprints — the deployment plan's vertex join and
 *       the restore-time fingerprint validation both depend on this.</li>
 * </ol>
 */
class TestDistributedScenarioSerialization {

    @TempDir
    java.nio.file.Path tempDir;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static ClusterLaunchConfig config(String... kv) {
        return ClusterLaunchConfig.parse(kv);
    }

    @Test
    void s1SpecIsSerializableAndRebuildsDeterministically() throws Exception {
        java.nio.file.Path eventsFile = tempDir.resolve("s1-events.json");
        DistributedScenarioSupport.writeS1EventsFile(eventsFile, ScenarioTestSupport.s1FullFixture());

        ClusterLaunchConfig cfg = config(
                "jobId=serialization-s1",
                "jdbcUrl=jdbc:h2:mem:serialization-s1;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "topicNamespace=serialization",
                "checkpointBaseDir=" + tempDir.resolve("cp"),
                "expectedNodeIds=tm-0,tm-1",
                "s1EventsFile=" + eventsFile,
                "s1EmitDelayMs=1",
                "s1FinishLingerMs=1");

        ClusterPipelineFactory.PipelineArtifacts artifacts =
                new S1ScenarioPipelineFactory().buildPipeline("serialization-s1", cfg);
        assertNotNull(artifacts.getPipelineSpec(), "S1 artifacts must carry the pipeline spec");
        assertTrue(artifacts.getJobGraph().getVertices().size() >= 10,
                "S1 graph must have the full scenario topology");

        assertSpecContract(artifacts);
    }

    @Test
    void s2SpecsAreSerializableAndRebuildDeterministically() throws Exception {
        java.nio.file.Path inputDir = tempDir.resolve("input");
        java.nio.file.Files.createDirectories(inputDir);
        java.nio.file.Files.write(inputDir.resolve("part-000.txt"),
                "u1,10,1700000000000".getBytes());

        int baseTransformCount = -1;
        for (String streamPath : new String[]{
                ScenarioTestSupport.S2_STREAM_PATH, ScenarioTestSupport.S2_DELTA_STREAM_PATH}) {
            ClusterLaunchConfig cfg = config(
                    "jobId=serialization-s2",
                    "jdbcUrl=jdbc:h2:mem:serialization-s2;MODE=MySQL;DB_CLOSE_DELAY=-1",
                    "topicNamespace=serialization",
                    "checkpointBaseDir=" + tempDir.resolve("cp"),
                    "expectedNodeIds=tm-0,tm-1",
                    "scenarioStreamPath=" + streamPath,
                    "s2InputDir=" + inputDir,
                    "s2OutputDir=" + tempDir.resolve("out-" + Math.abs(streamPath.hashCode())),
                    "s2LineDelayMs=1",
                    "s2FinishLingerMs=1");

            ClusterPipelineFactory.PipelineArtifacts artifacts =
                    new S2ScenarioPipelineFactory().buildPipeline("serialization-s2", cfg);
            assertNotNull(artifacts.getPipelineSpec(),
                    "S2 artifacts must carry the pipeline spec for " + streamPath);
            assertSpecContract(artifacts);

            // The delta's filter operator may CHAIN into an existing vertex, so the
            // JOB GRAPH vertex count is not guaranteed to grow — the DECLARED model
            // transforms are the honest comparison surface (the delta adds the
            // blacklist filter transform).
            int transformCount = ScenarioTestSupport.parseStreamXml(streamPath).getTransforms().size();
            if (baseTransformCount < 0) {
                baseTransformCount = transformCount;
            } else {
                assertTrue(transformCount > baseTransformCount,
                        "delta model must declare the blacklist filter transform");
            }
        }
    }

    /**
     * The cross-JVM deployment contract: the spec survives the Java round trip, and
     * an independent resolver rebuild (as each TM performs) yields the same vertex
     * id set and fingerprint as the coordinator-side build.
     */
    private void assertSpecContract(ClusterPipelineFactory.PipelineArtifacts artifacts) throws Exception {
        RemotePipelineSpec spec = DistributedScenarioSupport.roundTrip(artifacts.getPipelineSpec());

        // Independent rebuild from the (round-tripped) spec — the TM-side path.
        ScenarioXdslPipelineResolver resolver = new ScenarioXdslPipelineResolver();
        JobGraph tmSide = resolver.buildJobGraph(spec);

        JobGraph jcSide = artifacts.getJobGraph();
        assertEquals(
                new TreeSet<>(jcSide.getVertices().keySet()),
                new TreeSet<>(tmSide.getVertices().keySet()),
                "vertex ids must be IDENTICAL between the coordinator build and the TM-side "
                        + "spec rebuild (stable transformation ids)");

        assertNotNull(tmSide.getStreamModel(), "TM-side rebuild must populate the StreamModel");
        if (jcSide.getStreamModel() != null) {
            assertEquals(
                    jcSide.getStreamModel().computeFingerprint(),
                    tmSide.getStreamModel().computeFingerprint(),
                    "StreamModel fingerprints must be IDENTICAL between independent builds "
                            + "(restore-time fingerprint validation depends on this)");
        }
    }

    @Test
    void deserializedFileSourceIsRunnableAfterTransientReinit() throws Exception {
        // Regression for the DirectoryFileSourceFunction cross-JVM deserialization
        // defect (transient runEntered guard NPE): a deserialized instance must run
        // through its guard without NPE and emit the fixture lines.
        java.nio.file.Path inputDir = tempDir.resolve("src-input");
        java.nio.file.Files.createDirectories(inputDir);
        java.nio.file.Files.write(inputDir.resolve("part-000.txt"),
                "u1,10,1700000000000\nu2,20,1700000001000".getBytes());

        DirectoryFileSourceFunction original =
                new DirectoryFileSourceFunction(inputDir.toString(), 0L, 0L);
        DirectoryFileSourceFunction deserialized = DistributedScenarioSupport.roundTrip(original);

        java.util.List<String> collected = new java.util.ArrayList<>();
        deserialized.run(new CollectingLineContext(collected));
        assertEquals(2, collected.size(),
                "deserialized source must emit all fixture lines (transient guard re-initialized)");
    }

    /** Minimal collecting {@code SourceContext} for the deserialization regression. */
    private static final class CollectingLineContext
            implements io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<String> {
        private final List<String> collected;

        CollectingLineContext(List<String> collected) {
            this.collected = collected;
        }

        @Override
        public void collect(String element) {
            collected.add(element);
        }

        @Override
        public void collectWithTimestamp(String element, long timestamp) {
            collected.add(element);
        }

        @Override
        public void emitWatermark(long mark) {
        }

        @Override
        public void markAsTemporarilyIdle() {
        }

        @Override
        public long getProcessingTime() {
            return System.currentTimeMillis();
        }
    }

    @Test
    void lazyJdbcTemplateRoundTripsAndResolves() {
        // The S1 sink's lazy template must survive the round trip and rebuild the
        // delegate from the serialized connection info (resolved against a real H2
        // in-memory DB here — proves the proxy actually executes SQL end to end).
        String url = "jdbc:h2:mem:lazy-tpl-roundtrip;MODE=MySQL;DB_CLOSE_DELAY=-1";
        io.nop.dao.jdbc.IJdbcTemplate template = DistributedScenarioSupport.lazyJdbcTemplate(url, "sa", "");
        template.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("CREATE TABLE IF NOT EXISTS lazy_probe (id INT PRIMARY KEY)").end());
        template.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("INSERT INTO lazy_probe(id) VALUES(1)").end());

        io.nop.dao.jdbc.IJdbcTemplate deserialized = DistributedScenarioSupport.roundTrip(template);
        Integer count = deserialized.executeQuery(io.nop.core.lang.sql.SQL.begin()
                        .sql("SELECT COUNT(*) FROM lazy_probe").end(),
                ds -> ds.hasNext() ? ds.next().getInt(0) : 0);
        assertEquals(1, count, "deserialized lazy template must execute against the same DB");
    }

    /**
     * Ungated in-process proof of the FULL S1 topology through the remote-deploy
     * path (two in-process TaskManagers + RPC control plane + XDSL spec rebuild),
     * exercising the data-plane JSON codec over the 26-transform graph: CEP chains,
     * keyed enrichment, windows and the 2PC JDBC sink all run cross-"node" with
     * checkpoints + distributed commit notifications. Asserts mid-run-committed
     * rows (the W0 alerts), ledger durability and the durable manifest — the
     * full-set + kill/recover assertions belong to the gated multi-JVM tests.
     */
    @Test
    void s1FullTopologyRunsViaRemoteDeployInProcess() throws Exception {
        String url = "jdbc:h2:mem:s1-remote-inprocess;MODE=MySQL;DB_CLOSE_DELAY=-1";
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL(url);
        ds.setUser("sa");
        ds.setPassword("");
        io.nop.dao.jdbc.IJdbcTemplate jdbc = ScenarioTestSupport.newJdbcTemplate(ds);
        ScenarioTestSupport.createS1Tables(jdbc);
        for (String ledger : new String[]{"fraud_ledger_rapid", "fraud_ledger_unusual",
                "fraud_ledger_geo", "fraud_ledger_takeover"}) {
            ScenarioTestSupport.execute(jdbc, "DROP TABLE IF EXISTS " + ledger);
            DistributedScenarioSupport.alertSink(url, "sa", "", ledger).initializeLedgerTable();
        }

        io.nop.message.core.local.LocalMessageService messageService =
                new io.nop.message.core.local.LocalMessageService();
        io.nop.stream.runtime.cluster.InMemoryClusterRegistry registry =
                new io.nop.stream.runtime.cluster.InMemoryClusterRegistry();
        java.util.List<io.nop.stream.runtime.taskmanager.TaskManager> tms = new java.util.ArrayList<>();
        java.util.List<io.nop.stream.runtime.rpc.StreamControlRpcServer> servers = new java.util.ArrayList<>();
        java.util.List<io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory> proxies = new java.util.ArrayList<>();
        io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage storage =
                new io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage(
                        tempDir.resolve("cp").toString());

        try {
            String jobId = "s1-remote-inprocess";
            var beanResolver = DistributedScenarioSupport.s1DistributedResolver(
                    ScenarioTestSupport.s1FullFixture(), 60L, 500L, url, "sa", "");
            var model = ScenarioTestSupport.parseStreamXml(ScenarioTestSupport.S1_STREAM_PATH);
            var env = io.nop.stream.flow.builder.StreamModelDslBuilder.of(model, beanResolver).build();
            io.nop.stream.core.jobgraph.JobGraph jobGraph = env.buildJobGraph(jobId);
            var fp = jobGraph.getStreamModel() != null
                    ? jobGraph.getStreamModel().computeFingerprint() : null;
            var partitionedPlan = new io.nop.stream.core.graph.PartitionedPlanGenerator().generate(jobGraph, fp);
            var deploymentPlan = new io.nop.stream.runtime.execution.DeploymentPlanProviderImpl()
                    .generateLocal(partitionedPlan);

            long fencingEpoch = io.nop.stream.runtime.coordinator.JobCoordinator.deriveHaFencingEpoch(0L, 1L);
            java.util.Map<String, io.nop.stream.runtime.rpc.IStreamTaskRpcService> rpcs = new java.util.LinkedHashMap<>();
            for (int i = 0; i < 2; i++) {
                String nodeId = "tm-" + i;
                var tm = new io.nop.stream.runtime.taskmanager.TaskManager(nodeId, "rpc:" + nodeId, 16,
                        messageService, registry,
                        io.nop.stream.runtime.rpc.StreamControlRpcTopics.coordinatorTopic(jobId));
                tm.updateFencingToken(fencingEpoch);
                tm.start();
                tms.add(tm);
                var server = new io.nop.stream.runtime.rpc.StreamControlRpcServer(
                        "streamTaskRpc@" + nodeId, io.nop.stream.runtime.rpc.IStreamTaskRpcService.class,
                        tm, messageService, io.nop.stream.runtime.rpc.StreamControlRpcTopics.taskTopic(nodeId));
                server.start();
                servers.add(server);
                var proxy = new io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory(
                        "streamTaskRpc@" + nodeId, io.nop.stream.runtime.rpc.IStreamTaskRpcService.class,
                        messageService, io.nop.stream.runtime.rpc.StreamControlRpcTopics.taskTopic(nodeId));
                proxy.start();
                proxies.add(proxy);
                rpcs.put(nodeId, proxy.getProxy());
            }

            var cp = new io.nop.stream.runtime.checkpoint.CheckpointCoordinator(jobId, "pipeline-0",
                    new io.nop.stream.core.checkpoint.CheckpointIDCounter(), storage,
                    io.nop.stream.core.checkpoint.CheckpointConfig.builder()
                            .checkpointEnabled(true).checkpointInterval(300L)
                            .checkpointTimeout(30000L).maxConcurrentCheckpoints(1)
                            .maxRetainedCheckpoints(5).build());
            var coordinator = new io.nop.stream.runtime.coordinator.JobCoordinator(
                    jobId, "coordinator-" + jobId, deploymentPlan, registry, cp, rpcs);
            coordinator.setFencingEpoch(fencingEpoch);
            coordinator.setRemoteDeployMode(true);
            coordinator.setJobGraph(jobGraph);
            coordinator.setCheckpointStoragePath(storage.getBaseDir());
            coordinator.registerDistributedCommitForwarder();
            io.nop.stream.runtime.rpc.StreamControlRpcServer cs =
                    new io.nop.stream.runtime.rpc.StreamControlRpcServer("cc",
                            io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService.class, coordinator,
                            messageService, io.nop.stream.runtime.rpc.StreamControlRpcTopics.coordinatorTopic(jobId));
            io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory cpProxy =
                    new io.nop.stream.runtime.rpc.StreamControlRpcProxyFactory("cc",
                            io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService.class, messageService,
                            io.nop.stream.runtime.rpc.StreamControlRpcTopics.coordinatorTopic(jobId));
            try {
                io.nop.stream.runtime.rpc.IStreamCoordinatorRpcService coordRpc = cpProxy.getProxy();
                for (var tm : tms) {
                    tm.setCoordinatorRpcService(coordRpc);
                }
                cs.start();
                cpProxy.start();
                coordinator.start();
                coordinator.assignTasks();
                coordinator.startPeriodicCheckpoints(300L);

                // mid-run-committed rows: the W0 alerts (alice/bob) must be durably
                // committed while the bounded run is still flowing
                long deadline = System.currentTimeMillis() + 60_000L;
                while (System.currentTimeMillis() < deadline) {
                    if (ScenarioTestSupport.readAlertRows(jdbc).size() >= 2) {
                        break;
                    }
                    Thread.sleep(500L);
                }
                assertTrue(ScenarioTestSupport.readAlertRows(jdbc).size() >= 2,
                        "at least the W0 alert rows must be committed via the distributed "
                                + "commit chain; rows=" + ScenarioTestSupport.readAlertRows(jdbc));
                assertTrue(storage.loadLatestEpochManifest(jobId, "pipeline-0") != null,
                        "durable manifest must exist (multi-node ACK loop works)");
                assertTrue(!ScenarioTestSupport.readLedgerRows(jdbc, "fraud_ledger_rapid").isEmpty()
                                || !ScenarioTestSupport.readLedgerRows(jdbc, "fraud_ledger_unusual").isEmpty(),
                        "at least one chain ledger must record committed epochs");
                coordinator.stop();
            } finally {
                cpProxy.stop();
                cs.stop();
            }
        } finally {
            for (var p2 : proxies) p2.stop();
            for (var s2 : servers) s2.stop();
            for (var tm : tms) tm.stop();
        }
    }

    @Test
    void s1FixtureFileRoundTripIsLossless() throws Exception {
        List<Map<String, Object>> fixture = ScenarioTestSupport.s1FullFixture();
        java.nio.file.Path file = tempDir.resolve("events.json");
        DistributedScenarioSupport.writeS1EventsFile(file, fixture);
        List<Map<String, Object>> readBack = DistributedScenarioSupport.readS1EventsFile(file);
        assertEquals(fixture.size(), readBack.size(), "fixture event count must survive JSON round trip");
        for (int i = 0; i < fixture.size(); i++) {
            assertEquals(fixture.get(i), readBack.get(i), "event " + i + " must be identical");
        }
    }
}
