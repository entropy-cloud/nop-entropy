/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.nop.stream.core.metrics.StreamMetricsRegistries;

/**
 * Single access facade for the process composite metrics registry (WI6 plan 2416).
 *
 * <p>Runtime call sites must obtain the registry through this facade (or through the
 * {@link TaskNodeMetrics} / {@link EngineMetrics} wrappers) instead of reaching into
 * the core-level {@link StreamMetricsRegistries} singleton directly — the wrapper
 * layer owns registry access. Exception register: the two windowing operator call
 * sites (WindowOperator / OverWindowOperator) stay on the direct reference until WI7
 * relocates the package into core, where the closure moves to the core-side mechanism.
 */
public final class RuntimeMetrics {

    private RuntimeMetrics() {
    }

    /**
     * Returns the process composite {@link MeterRegistry}.
     */
    public static MeterRegistry registry() {
        return StreamMetricsRegistries.registry();
    }
}
