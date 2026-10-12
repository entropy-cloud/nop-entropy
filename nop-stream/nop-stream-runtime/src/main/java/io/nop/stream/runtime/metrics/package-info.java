/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
/**
 * Metrics wrapper layer: {@link io.nop.stream.runtime.metrics.EngineMetrics} / {@link io.nop.stream.runtime.metrics.TaskNodeMetrics} / {@link io.nop.stream.runtime.metrics.RuntimeMetrics}. Runtime components must go through these wrappers, not call {@code StreamMetricsRegistries.registry()} inline (WI6 fan-out cleanup).
 */
package io.nop.stream.runtime.metrics;
