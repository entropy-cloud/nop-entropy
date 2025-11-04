/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package io.nop.commons.collections;

import java.util.PrimitiveIterator;

/**
 * A resizable, ordered or unordered int array. Avoids the boxing that occurs with ArrayList<Integer>. If unordered,
 * this class avoids a memory copy when removing elements (the last element is moved to the removed element's position).
 *
 * @author Nathan Sweet
 */
public interface IntArray extends Iterable<Integer> {
    default PrimitiveIterator.OfInt iterator() {
        return new PrimitiveIterator.OfInt() {
            int index;

            @Override
            public int nextInt() {
                return get(index++);
            }

            @Override
            public boolean hasNext() {
                return index < size();
            }
        };
    }

    boolean isOrdered();

    IntArray sort();

    int size();

    default boolean isEmpty() {
        return size() == 0;
    }

    int get(int index);

    int indexOf(int value);

    default boolean contains(int value) {
        return indexOf(value) >= 0;
    }

    int lastIndexOf(int value);

    default void copyTo(int[] to) {
        copyTo(to, 0);
    }

    void copyTo(int[] to, int toIndex);

    MutableIntArray toMutable();

    ImmutableIntArray toImmutable();

    /**
     * 如果是mutable的IntArray，则直接将输入数组合并到当前数组上。如果不是可以修改的IntArray，则返回一个合并后的MutableIntArray
     * merge操作只会加入不重复的值。如果array中的所有值在当前数组中都存在，则返回当前数组，并不会作任何修改。
     *
     * @param array 需要合并的数组
     * @return 当前IntArray或者新建的IntArray
     */
    IntArray merge(IntArray array);

    /**
     * 如果可以修改，则合并到当前IntArray上，否则调用toMutable()先得到一个可修改的数组，然后再合并输入值。 如果value在当前数组中已存在，则直接返回当前数组。
     *
     * @param value 需要加入到集合中的值
     * @return 当前IntArray或者新建的IntArray
     */
    IntArray merge(int value);

    default IntArray merge(int[] values) {
        IntArray array = this;
        for (int i = 0, n = values.length; i < n; i++) {
            array = merge(values[i]);
        }
        return array;
    }

    boolean isEqual(IntArray array);

    default int[] indexOf(IntArray array) {
        int[] ret = new int[array.size()];
        for (int i = 0, n = array.size(); i < n; i++) {
            int index = indexOf(array.get(i));
            ret[i] = index;
        }
        return ret;
    }
}
