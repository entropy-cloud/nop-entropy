/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.util.Map;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.runtime.rpc.RemotePipelineResolver;
import io.nop.stream.runtime.rpc.RemotePipelineSpec;

/**
 * Item 14 (composite-scenario distributed): rebuilds a scenario JobGraph on the
 * TaskManager side from a {@link RemotePipelineSpec}. Registered via
 * {@code META-INF/services} (test classpath) so every spawned cluster JVM — which
 * shares the test classpath — discovers it through the {@code SubtaskPlanBuilder}
 * ServiceLoader seam.
 *
 * <p>Determinism contract: the rebuild goes through the exact same parse →
 * DSL-builder → {@link StreamExecutionEnvironment#buildJobGraph} pipeline the
 * coordinator-side factory used, and {@code buildJobGraph} assigns STABLE
 * transformation ids (derived from transformation names), so the locally rebuilt
 * graph is identical across JVMs (vertex ids + StreamModel fingerprint) — the
 * deployment plan's vertex references join cleanly and restore-time fingerprint
 * validation passes.
 */
public class ScenarioXdslPipelineResolver implements RemotePipelineResolver {

    @Override
    public JobGraph buildJobGraph(RemotePipelineSpec spec) throws Exception {
        // The spawned JVM's main() already initialized nop-core; ensure for the
        // in-process paths that call this resolver directly. Idempotent.
        CoreInitialization.initialize();

        io.nop.stream.flow.model.StreamModel model = ScenarioTestSupport.parseStreamXml(
                spec.getStreamVfsPath());
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        for (Map.Entry<String, Object> entry : spec.getBeanBindings().entrySet()) {
            resolver.register(entry.getKey(), entry.getValue());
        }
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        return env.buildJobGraph(spec.getStreamVfsPath());
    }
}
