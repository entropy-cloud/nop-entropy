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

import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.ClusterPipelineFactory;

/**
 * Item 14 (composite-scenario distributed): S2 pipeline factory for the
 * {@code JobCoordinatorMain} launch seam. Parses the S2 XDSL declaration (base or
 * Delta variant — plan-2 assets), assembles the distributed bean set (bounded file
 * source with per-line pacing + exactly-once file sink, both path-configured and
 * serializable), and produces the deployable artifacts.
 *
 * <p>Launch parameters (via {@code MiniStreamCluster.withCoordinatorArg}):
 * <ul>
 *   <li>{@code scenarioStreamPath} (required): VFS path of the S2 stream model —
 *       base ({@code /nop/stream/demo/fraud-s2-file.stream.xml}) or Delta variant
 *       ({@code .../fraud-s2-file-delta.stream.xml});</li>
 *   <li>{@code s2InputDir} (required): input directory (fixture files written by the test);</li>
 *   <li>{@code s2OutputDir} (required): {@code FileTwoPhaseCommitSink} output directory
 *       (shared filesystem observable across JVMs);</li>
 *   <li>{@code s2LineDelayMs} / {@code s2FinishLingerMs}: source pacing;</li>
 *   <li>{@code scenarioCheckpointIntervalMs} / {@code scenarioCheckpointTimeoutMs} /
 *       {@code scenarioMaxRetainedCheckpoints}: checkpoint tuning.</li>
 * </ul>
 */
public class S2ScenarioPipelineFactory implements ClusterPipelineFactory {

    @Override
    public PipelineArtifacts buildPipeline(String jobId, ClusterLaunchConfig config) throws Exception {
        String streamPath = config.require(DistributedScenarioSupport.KEY_STREAM_PATH);
        String inputDir = config.require(DistributedScenarioSupport.KEY_S2_INPUT_DIR);
        String outputDir = config.require(DistributedScenarioSupport.KEY_S2_OUTPUT_DIR);
        if (!Files.isDirectory(Paths.get(inputDir))) {
            throw new IllegalArgumentException("s2InputDir is not a directory: " + inputDir);
        }

        long lineDelayMs = config.getLong(DistributedScenarioSupport.KEY_S2_LINE_DELAY, 100L);
        long finishLingerMs = config.getLong(DistributedScenarioSupport.KEY_S2_LINGER, 1000L);
        // Item 14 (matrix C3): optional in-sink throttling (0 = plain production sinks).
        long sinkThrottleMs = config.getLong(DistributedScenarioSupport.KEY_SINK_THROTTLE_MS, 0L);
        String throttleReleaseFile = config.get(DistributedScenarioSupport.KEY_THROTTLE_RELEASE_FILE, "");
        // Item 15 (BP-1): optional live-stepped throttle via level file (exclusive
        // with the C3 static throttle — validated in s2DistributedResolver).
        String throttleLevelFile = config.get(DistributedScenarioSupport.KEY_THROTTLE_LEVEL_FILE, "");
        // Item 32 (OBS-1): optional consumer-side variant — the same live-stepped
        // level file applied at the window assigner (the consumer vertex's
        // per-record path; exclusive with both sink throttle forms — validated
        // in s2DistributedResolver).
        String consumerThrottleLevelFile = config.get(
                DistributedScenarioSupport.KEY_CONSUMER_THROTTLE_LEVEL_FILE, "");

        InMemoryBeanFunctionResolver resolver = DistributedScenarioSupport.s2DistributedResolver(
                inputDir, outputDir, lineDelayMs, finishLingerMs,
                sinkThrottleMs, throttleReleaseFile.isBlank() ? null : throttleReleaseFile,
                throttleLevelFile.isBlank() ? null : throttleLevelFile,
                consumerThrottleLevelFile.isBlank() ? null : consumerThrottleLevelFile);

        io.nop.stream.flow.model.StreamModel model = ScenarioTestSupport.parseStreamXml(streamPath);
        return DistributedScenarioSupport.xdslArtifacts(streamPath, model, resolver, config);
    }
}
