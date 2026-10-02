package io.nop.stream.flow.model;

import io.nop.stream.flow.model._gen._StreamAggregatorModel;

/**
 * WI8c: hand-written retention wrapper over the generated aggregate-descriptor model
 * (same pattern as {@link WindowingStrategyModel}). Entries are pure parameter
 * descriptors — fnId plus the value expression — referenced from
 * {@code <aggregate aggregatorRef>}; the accumulation semantics come from the
 * aggregate evaluator provider, never from this model.
 */
public class StreamAggregatorModel extends _StreamAggregatorModel {
    public StreamAggregatorModel() {

    }
}
