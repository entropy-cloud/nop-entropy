/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Files;
import java.nio.file.Paths;

import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.ClusterPipelineFactory;

/**
 * CONN-01 successor (roadmap item 35): parallel-2PC JDBC scenario pipeline factory
 * for the {@code JobCoordinatorMain} launch seam (multi-JVM form of
 * {@code fraud-parallel-2pc-jdbc.stream.xml}). The P=2 keyed chain + 2PC sink
 * beans are all serializable (named classes, lazy JDBC template), so the graph
 * survives the deployTask Java serialization to real TaskManager JVMs; the ledger
 * and data tables live on the shared cluster H2 (AUTO_SERVER) so per-subtask
 * commits from different JVMs land in ONE ledger.
 *
 * <p>Launch parameters (via {@code MiniStreamCluster.withCoordinatorArg}):
 * {@code p2pcInputDir} (required), {@code p2pcLineDelayMs} /
 * {@code p2pcFinishLingerMs} (source pacing), plus the shared checkpoint tuning
 * keys ({@code scenarioCheckpointIntervalMs} etc.).
 */
public class Parallel2PcScenarioPipelineFactory implements ClusterPipelineFactory {

    public static final String KEY_INPUT_DIR = "p2pcInputDir";
    public static final String KEY_LINE_DELAY = "p2pcLineDelayMs";
    public static final String KEY_LINGER = "p2pcFinishLingerMs";

    @Override
    public PipelineArtifacts buildPipeline(String jobId, ClusterLaunchConfig config) throws Exception {
        String inputDir = config.require(KEY_INPUT_DIR);
        if (!Files.isDirectory(Paths.get(inputDir))) {
            throw new IllegalArgumentException("p2pcInputDir does not exist: " + inputDir);
        }
        long lineDelayMs = config.getLong(KEY_LINE_DELAY, 250L);
        long finishLingerMs = config.getLong(KEY_LINGER, 800L);

        String jdbcUrl = config.require(ClusterLaunchConfig.KEY_JDBC_URL);
        String jdbcUser = config.get(ClusterLaunchConfig.KEY_JDBC_USER, "sa");
        String jdbcPassword = config.get(ClusterLaunchConfig.KEY_JDBC_PASSWORD, "");

        InMemoryBeanFunctionResolver resolver = ParallelTwoPhaseCommitScenarios.distributedJdbcResolver(
                inputDir, jdbcUrl, jdbcUser, jdbcPassword, lineDelayMs, finishLingerMs);

        io.nop.stream.flow.model.StreamModel model = ScenarioTestSupport.parseStreamXml(
                ParallelTwoPhaseCommitScenarios.JDBC_STREAM_PATH);
        return DistributedScenarioSupport.xdslArtifacts(
                ParallelTwoPhaseCommitScenarios.JDBC_STREAM_PATH, model, resolver, config);
    }
}
