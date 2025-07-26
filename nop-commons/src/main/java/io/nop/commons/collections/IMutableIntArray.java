package io.nop.commons.collections;

public interface IMutableIntArray extends IntArray {
    void set(int index, int value);

    void add(int value);
}
