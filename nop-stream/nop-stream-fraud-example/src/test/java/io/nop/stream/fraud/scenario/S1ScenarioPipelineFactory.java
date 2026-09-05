/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.ClusterPipelineFactory;

/**
 * Item 14 (composite-scenario distributed): S1 pipeline factory for the
 * {@code JobCoordinatorMain} launch seam. Parses the S1 XDSL declaration
 * ({@code /nop/stream/demo/fraud-s1-cdc.stream.xml}, plan-2 asset), assembles the
 * distributed bean set (serializable — survives the deployTask Java serialization),
 * reads the deterministic CDC event fixture from the file the test wrote, and
 * produces the deployable artifacts. The S1 JDBC 2PC sinks point at the shared
 * cluster H2 via the lazy template (tables pre-created by the harness).
 *
 * <p>Launch parameters (via {@code MiniStreamCluster.withCoordinatorArg}):
 * <ul>
 *   <li>{@code s1EventsFile} (required): JSON fixture file path
 *       ({@link DistributedScenarioSupport#writeS1EventsFile});</li>
 *   <li>{@code s1EmitDelayMs} / {@code s1FinishLingerMs}: source pacing
 *       (multi-JVM defaults are slower than LOCAL so periodic checkpoints
 *       interleave with emission);</li>
 *   <li>{@code scenarioCheckpointIntervalMs} / {@code scenarioCheckpointTimeoutMs} /
 *       {@code scenarioMaxRetainedCheckpoints}: checkpoint tuning.</li>
 * </ul>
 */
public class S1ScenarioPipelineFactory implements ClusterPipelineFactory {

    @Override
    public PipelineArtifacts buildPipeline(String jobId, ClusterLaunchConfig config) throws Exception {
        String eventsFile = config.require(DistributedScenarioSupport.KEY_S1_EVENTS_FILE);
        if (!Files.exists(Paths.get(eventsFile))) {
            throw new IllegalArgumentException("s1EventsFile does not exist: " + eventsFile);
        }
        List<Map<String, Object>> eventSpecs = DistributedScenarioSupport.readS1EventsFile(
                Paths.get(eventsFile));

        long emitDelayMs = config.getLong(DistributedScenarioSupport.KEY_S1_EMIT_DELAY, 100L);
        long finishLingerMs = config.getLong(DistributedScenarioSupport.KEY_S1_LINGER, 1000L);
        // Item 14 (matrix C3): optional in-sink throttling (0 = plain production sinks).
        long sinkThrottleMs = config.getLong(DistributedScenarioSupport.KEY_SINK_THROTTLE_MS, 0L);
        String throttleReleaseFile = config.get(DistributedScenarioSupport.KEY_THROTTLE_RELEASE_FILE, "");

        String jdbcUrl = config.require(ClusterLaunchConfig.KEY_JDBC_URL);
        String jdbcUser = config.get(ClusterLaunchConfig.KEY_JDBC_USER, "sa");
        String jdbcPassword = config.get(ClusterLaunchConfig.KEY_JDBC_PASSWORD, "");

        InMemoryBeanFunctionResolver resolver = DistributedScenarioSupport.s1DistributedResolver(
                eventSpecs, emitDelayMs, finishLingerMs, jdbcUrl, jdbcUser, jdbcPassword,
                sinkThrottleMs, throttleReleaseFile.isBlank() ? null : throttleReleaseFile);

        io.nop.stream.flow.model.StreamModel model = ScenarioTestSupport.parseStreamXml(
                ScenarioTestSupport.S1_STREAM_PATH);
        return DistributedScenarioSupport.xdslArtifacts(
                ScenarioTestSupport.S1_STREAM_PATH, model, resolver, config);
    }
}
