package io.nop.commons.aggregator;

public interface IAggregatorProvider {
    String AGGREGATOR_SUM = "sum";
    String AGGREGATOR_AVG = "avg";
    String AGGREGATOR_MAX = "max";
    String AGGREGATOR_MIN = "min";
    String AGGREGATOR_COUNT = "count";

    <V, R> IAggregator newAggregator(String name);
}
