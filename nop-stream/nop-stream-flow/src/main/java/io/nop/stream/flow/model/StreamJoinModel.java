package io.nop.stream.flow.model;

import io.nop.stream.flow.model._gen._StreamJoinModel;

/**
 * WI8d: hand-written retention wrapper over the generated join-transform model
 * (same pattern as {@link StreamAggregatorModel}). Declares an equi-join via
 * {@code joinRef}; build-time validation lives in the DSL builder, runtime
 * consumption in WI13's buildJoin.
 */
public class StreamJoinModel extends _StreamJoinModel {
    public StreamJoinModel() {

    }
}
