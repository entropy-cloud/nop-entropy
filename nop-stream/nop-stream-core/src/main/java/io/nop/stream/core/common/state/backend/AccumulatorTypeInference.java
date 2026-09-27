/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.common.functions.AggregateFunction;

/**
 * Shared (plan 2278 Phase 2 convergence) accumulator-type inference for the
 * aggregating-state restore path, used by the core {@code MemoryStateSerDe} and
 * the rocksdb {@code RocksDBSnapshotSerDe} — the two previously carried verbatim
 * copies.
 *
 * <p>The window descriptor path (WindowedStreamImpl.aggregate/reduce) records the
 * accumulator type as {@code java.lang.Object} (generic erasure), so the snapshot's
 * recorded valueType cannot drive value re-materialization: a JSON array round-trip
 * of a {@code long[]} accumulator would be restored as an ArrayList and the user
 * function's {@code add} would ClassCastException. When the recorded type is the
 * generic {@code Object}, infer the real accumulator type from the LIVE aggregate
 * function's {@code createAccumulator()} (registered by the operator before restore).
 * Functions whose {@code createAccumulator()} returns {@code null} (e.g. the
 * reduce-function wrapper) keep the recorded type — JSON-native accumulators
 * (String/numbers) restore correctly without the inference.
 *
 * <p>The WARN text follows the core wording; the rocksdb copy used a different
 * phrasing (log text only — registered in plan 2278 as a non-behavioral change).
 */
public final class AccumulatorTypeInference {

    private static final Logger LOG = LoggerFactory.getLogger(AccumulatorTypeInference.class);

    private AccumulatorTypeInference() {
    }

    public static Class<?> inferAccumulatorType(AggregateFunction<?, ?, ?> aggregateFunction, Class<?> recordedType) {
        if (recordedType != Object.class || aggregateFunction == null) {
            return recordedType;
        }
        try {
            Object accumulator = aggregateFunction.createAccumulator();
            if (accumulator != null) {
                return accumulator.getClass();
            }
        } catch (Exception e) {
            // Keep the recorded (generic) type; JSON-native accumulators restore
            // correctly either way — but the live-function failure must be visible
            // (it surfaces later as a ClassCastException in user add() otherwise).
            LOG.warn("createAccumulator() on aggregate function {} threw; keeping recorded type {}",
                    aggregateFunction.getClass().getName(), recordedType.getName(), e);
        }
        return recordedType;
    }
}
