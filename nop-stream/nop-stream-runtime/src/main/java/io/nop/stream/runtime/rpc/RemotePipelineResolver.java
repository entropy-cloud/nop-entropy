/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.stream.core.jobgraph.JobGraph;

/**
 * Item 14 (composite-scenario distributed): rebuilds a {@link JobGraph} on the
 * TaskManager side from a {@link RemotePipelineSpec}. The declaration format (XDSL
 * today) is owned by the flow / upper scenario modules, and the runtime deployment
 * path should not know any specific declaration syntax — implementations are
 * therefore provided via {@link java.util.ServiceLoader} by whoever owns the
 * declaration format on the classpath (the scenario module registers its XDSL
 * resolver under {@code META-INF/services}).
 *
 * <p>Note: runtime does hold a compile dependency on {@code nop-stream-flow} for the
 * maintenance tooling (see the dependency note in {@code pom.xml}); this SPI keeps
 * the TM-side rebuild decoupled from the declaration syntax, so other declaration
 * formats can ship their own resolvers without touching runtime.
 *
 * <p>Contract: the rebuilt graph must be IDENTICAL across JVMs for the same spec —
 * implementations must build through {@code StreamExecutionEnvironment#buildJobGraph}
 * (stable transformation ids) and never rely on JVM-local ordering or counters.
 */
public interface RemotePipelineResolver {

    /**
     * Rebuilds the deployable JobGraph from the declaration spec.
     *
     * @param spec the pipeline declaration (VFS model path + serializable beans)
     * @return the locally-built JobGraph; never null
     * @throws Exception on any parse/build failure (fails the deployment fast)
     */
    JobGraph buildJobGraph(RemotePipelineSpec spec) throws Exception;
}
