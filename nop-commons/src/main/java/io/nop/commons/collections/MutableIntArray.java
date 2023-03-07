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

import java.util.Arrays;

/**
 * A resizable, ordered or unordered int array. Avoids the boxing that occurs with ArrayList<Integer>. If unordered,
 * this class avoids a memory copy when removing elements (the last element is moved to the removed element's position).
 *
 * @author Nathan Sweet
 */
public class MutableIntArray implements IntArray {
    private int[] items;
    private int size;
    private boolean ordered;

    /**
     * Creates an ordered array with a capacity of 16.
     */
    public MutableIntArray() {
        this(true, 16);
    }

    /**
     * Creates an ordered array with the specified capacity.
     */
    public MutableIntArray(int capacity) {
        this(true, capacity);
    }

    /**
     * @param ordered  If false, methods that remove elements may change the order of other elements in the array, which
     *                 avoids a memory copy.
     * @param capacity Any elements added beyond this will cause the backing array to be grown.
     */
    public MutableIntArray(boolean ordered, int capacity) {
        this.ordered = ordered;
        items = new int[capacity];
    }

    /**
     * Creates a new array containing the elements in the specific array. The new array will be ordered if the specific
     * array is ordered. The capacity is set to the number of elements, so any subsequent elements added will cause the
     * backing array to be grown.
     */
    public MutableIntArray(IntArray array) {
        this.ordered = array.isOrdered();
        this.size = array.size();
        items = new int[size];
        array.copyTo(items);
    }

    public void copyTo(int[] to, int toIndex) {
        System.arraycopy(items, 0, to, toIndex, size);
    }

    public MutableIntArray toMutable() {
        return this;
    }

    @Override
    public ImmutableIntArray toImmutable() {
        return new ImmutableIntArray(toArray());
    }

    /**
     * Creates a new ordered array containing the elements in the specified array. The capacity is set to the number of
     * elements, so any subsequent elements added will cause the backing array to be grown.
     */
    public MutableIntArray(int[] array) {
        this(true, array);
    }

    /**
     * Creates a new array containing the elements in the specified array. The capacity is set to the number of
     * elements, so any subsequent elements added will cause the backing array to be grown.
     *
     * @param ordered If false, methods that remove elements may change the order of other elements in the array, which
     *                avoids a memory copy.
     */
    public MutableIntArray(boolean ordered, int[] array) {
        this(ordered, array.length);
        size = array.length;
        System.arraycopy(array, 0, items, 0, size);
    }

    public boolean isOrdered() {
        return ordered;
    }

    public MutableIntArray add(int value) {
        int[] items = this.items;
        if (size == items.length)
            items = resize(Math.max(8, (int) (size * 1.75f)));
        items[size++] = value;
        return this;
    }

    public MutableIntArray addAll(IntArray array) {
        return addAll(array, 0, array.size());
    }

    public MutableIntArray addAll(IntArray array, int offset, int length) {
        if (offset + length > array.size())
            throw new IllegalArgumentException(
                    "offset + length must be <= size: " + offset + " + " + length + " <= " + array.size());

        int[] items = this.items;
        int sizeNeeded = size + length - offset;
        if (sizeNeeded >= items.length)
            items = resize(Math.max(8, (int) (sizeNeeded * 1.75f)));
        array.copyTo(items, size);
        size += length;
        return this;
    }

    public MutableIntArray addAll(int[] array) {
        return addAll(array, 0, array.length);
    }

    public MutableIntArray addAll(int[] array, int offset, int length) {
        int[] items = this.items;
        int sizeNeeded = size + length - offset;
        if (sizeNeeded >= items.length)
            items = resize(Math.max(8, (int) (sizeNeeded * 1.75f)));
        System.arraycopy(array, offset, items, size, length);
        size += length;
        return this;
    }

    public int get(int index) {
        if (index >= size)
            throw new IndexOutOfBoundsException(String.valueOf(index));
        return items[index];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size <= 0;
    }

    public void set(int index, int value) {
        if (index >= size)
            throw new IndexOutOfBoundsException(String.valueOf(index));
        items[index] = value;
    }

    public void insert(int index, int value) {
        int[] items = this.items;
        if (size == items.length)
            items = resize(Math.max(8, (int) (size * 1.75f)));
        if (ordered)
            System.arraycopy(items, index, items, index + 1, size - index);
        else
            items[size] = items[index];
        size++;
        items[index] = value;
    }

    public void swap(int first, int second) {
        if (first >= size)
            throw new IndexOutOfBoundsException(String.valueOf(first));
        if (second >= size)
            throw new IndexOutOfBoundsException(String.valueOf(second));
        int[] items = this.items;
        int firstValue = items[first];
        items[first] = items[second];
        items[second] = firstValue;
    }

    public boolean contains(int value) {
        int i = size - 1;
        int[] items = this.items;
        while (i >= 0)
            if (items[i--] == value)
                return true;
        return false;
    }

    public int indexOf(int value) {
        int[] items = this.items;
        for (int i = 0, n = size; i < n; i++)
            if (items[i] == value)
                return i;
        return -1;
    }

    public int lastIndexOf(int value) {
        int[] items = this.items;
        for (int i = size - 1; i >= 0; i--) {
            if (items[i] == value)
                return i;
        }
        return -1;
    }

    public boolean removeValue(int value) {
        int[] items = this.items;
        for (int i = 0, n = size; i < n; i++) {
            if (items[i] == value) {
                removeIndex(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes and returns the item at the specified index.
     */
    public int removeIndex(int index) {
        if (index >= size)
            throw new IndexOutOfBoundsException(String.valueOf(index));
        int[] items = this.items;
        int value = items[index];
        size--;
        if (ordered)
            System.arraycopy(items, index + 1, items, index, size - index);
        else
            items[index] = items[size];
        return value;
    }

    public void push(int value) {
        add(value);
    }

    /**
     * Removes and returns the last item.
     */
    public int pop() {
        return items[--size];
    }

    /**
     * Returns the last item.
     */
    public int peek() {
        return items[size - 1];
    }

    public void clear() {
        size = 0;
    }

    /**
     * Reduces the size of the backing array to the size of the actual items. This is useful to release memory when many
     * items have been removed, or if it is known that more items will not be added.
     */
    public void shrink() {
        resize(size);
    }

    /**
     * Increases the size of the backing array to acommodate the specified number of additional items. Useful before
     * adding many items to avoid multiple backing array resizes.
     *
     * @return {@link #items}
     */
    public int[] ensureCapacity(int additionalCapacity) {
        int sizeNeeded = size + additionalCapacity;
        if (sizeNeeded >= items.length)
            resize(Math.max(8, sizeNeeded));
        return items;
    }

    protected int[] resize(int newSize) {
        int[] newItems = new int[newSize];
        int[] items = this.items;
        System.arraycopy(items, 0, newItems, 0, Math.min(items.length, newItems.length));
        this.items = newItems;
        return newItems;
    }

    public IntArray sort() {
        int[] copy = new int[size];
        System.arraycopy(items, 0, copy, 0, size);
        Arrays.sort(copy);
        return new ImmutableIntArray(copy, size, true);
    }

    public void reverse() {
        for (int i = 0, lastIndex = size - 1, n = size / 2; i < n; i++) {
            int ii = lastIndex - i;
            int temp = items[i];
            items[i] = items[ii];
            items[ii] = temp;
        }
    }

    /**
     * Reduces the size of the array to the specified size. If the array is already smaller than the specified size, no
     * action is taken.
     */
    public void truncate(int newSize) {
        if (size > newSize)
            size = newSize;
    }

    public int[] toArray() {
        int[] array = new int[size];
        System.arraycopy(items, 0, array, 0, size);
        return array;
    }

    @Override
    public IntArray merge(IntArray array) {
        if (this == array)
            return this;

        for (int i = 0, n = array.size(); i < n; i++) {
            int value = array.get(i);
            if (indexOf(value) < 0) {
                add(value);
            }
        }
        return this;
    }

    @Override
    public IntArray merge(int value) {
        if (contains(value))
            return this;
        return add(value);
    }

    public String toString() {
        if (size == 0)
            return "[]";
        int[] items = this.items;
        StringBuilder buffer = new StringBuilder(32);
        buffer.append('[');
        buffer.append(items[0]);
        for (int i = 1; i < size; i++) {
            buffer.append(", ");
            buffer.append(items[i]);
        }
        buffer.append(']');
        return buffer.toString();
    }

    public String toString(String separator) {
        if (size == 0)
            return "";
        int[] items = this.items;
        StringBuilder buffer = new StringBuilder(32);
        buffer.append(items[0]);
        for (int i = 1; i < size; i++) {
            buffer.append(separator);
            buffer.append(items[i]);
        }
        return buffer.toString();
    }

    @Override
    public boolean isEqual(IntArray o) {
        if (this == o)
            return true;
        if (size != o.size())
            return false;

        int[] items = this.items;
        for (int i = 0, n = size; i < n; i++) {
            if (items[i] != o.get(i))
                return false;
        }
        return true;
    }

    public int tryPeek() {
        if (size <= 0)
            return -1;
        return items[size - 1];
    }

    public int tryPeek(int n) {
        if (size - n <= 0)
            return -1;
        return items[size - 1 - n];
    }

    /**
     * Replace the value on the top of the stack with the given value.
     */
    public void replaceTop(int topOfStack) {
        items[size - 1] = topOfStack;
    }
}
