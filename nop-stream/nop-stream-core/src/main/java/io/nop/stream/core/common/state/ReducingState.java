package io.nop.stream.core.common.state;

public interface ReducingState<T> extends State {
    T get() throws Exception;

    void add(T value) throws Exception;
}
