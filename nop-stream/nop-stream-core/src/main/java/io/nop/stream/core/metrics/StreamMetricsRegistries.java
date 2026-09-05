/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

/**
 * Process-level composite meter registry for all {@code nop.stream.*} metrics
 * (item 16, P-REQ-1). Lives in core so every nop-stream module (core /
 * rocksdb / runtime) registers into the same composite.
 *
 * <p>Structure: a composite registry whose FIRST member is an in-process
 * {@code SimpleMeterRegistry} (the accumulation store), to which backend
 * registries (e.g. PrometheusMeterRegistry) are attached as additional
 * members when exposure is enabled.
 *
 * <p>Why not swap {@code GlobalMeterRegistry}'s single instance: a swap
 * leaves every already-registered meter in the old registry, invisible to the
 * new one. With this composite, meters are registered once; a backend member
 * attached later receives every increment from its attachment point onward,
 * and the base member always retains the full history (what in-process
 * queries and tests read).
 */
public final class StreamMetricsRegistries {

    private static final CompositeMeterRegistry COMPOSITE =
            new CompositeMeterRegistry(io.micrometer.core.instrument.Clock.SYSTEM,
                    java.util.List.of(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

    private StreamMetricsRegistries() {
    }

    public static CompositeMeterRegistry registry() {
        return COMPOSITE;
    }

    /**
     * Attaches a backend registry (e.g. PrometheusMeterRegistry) as a member of
     * the composite. All meters registered into the composite — past and
     * future — become visible to the member.
     */
    public static void addMember(MeterRegistry member) {
        COMPOSITE.add(member);
    }

    /**
     * Detaches a previously attached member registry (used on ops server shutdown).
     */
    public static void removeMember(MeterRegistry member) {
        COMPOSITE.remove(member);
    }
}
