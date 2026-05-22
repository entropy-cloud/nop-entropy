package io.nop.stream.core.common.state;

public interface AggregatingState<IN, OUT> extends State {
    OUT get() throws Exception;

    void add(IN value) throws Exception;
}
