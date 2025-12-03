package io.nop.commons.aggregator;

public interface IAggregator {
    void update(Object value);

    Object getValue();

    default void reset() {

    }
}
