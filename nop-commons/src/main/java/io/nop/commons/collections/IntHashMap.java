/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package io.nop.commons.collections;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.MathHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator.OfInt;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

/**
 * An unordered map that uses int keys. This implementation is a cuckoo hash map using 3 hashes (if table size is less
 * than 2^16) or 4 hashes (if table size is greater than or equal to 2^16), random walking, and a small stash for
 * problematic keys. Null values are allowed. No allocation is done except when growing the table size. <br>
 * <br>
 * This map performs very fast get, containsKey, and remove (typically O(1), worst case O(log(n))). Put may be a bit
 * slower, depending on hash collisions. Load factors greater than 0.91 greatly increase the chances the map will have
 * to rehash to the next higher POT size.
 *
 * @author Nathan Sweet
 */
public class IntHashMap<V> implements MapOfInt<V> {
    // primes for hash functions 2, 3, and 4
    private static final int PRIME2 = 0xbe1f14b1;
    private static final int PRIME3 = 0xb4b82e39;
    private static final int PRIME4 = 0xced1c241;
    private static final int EMPTY = 0;

    private int size;

    private int[] keyTable;
    private V[] valueTable;
    private int capacity, stashSize;
    private V zeroValue;
    private boolean hasZeroValue;

    private float loadFactor;
    private int hashShift, mask, threshold;
    private int stashCapacity;
    private int pushIterations;
    private boolean isBigTable;

    /**
     * Creates a new map with an initial capacity of 32 and a load factor of 0.8. This map will hold 25 items before
     * growing the backing table.
     */
    public IntHashMap() {
        this(32, 0.8f);
    }

    /**
     * Creates a new map with a load factor of 0.8. This map will hold initialCapacity * 0.8 items before growing the
     * backing table.
     */
    public IntHashMap(int initialCapacity) {
        this(initialCapacity, 0.8f);
    }

    /**
     * Creates a new map with the specified initial capacity and load factor. This map will hold initialCapacity *
     * loadFactor items before growing the backing table.
     */
    @SuppressWarnings("unchecked")
    public IntHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("initialCapacity must be >= 0: " + initialCapacity);
        if (initialCapacity > 1 << 30)
            throw new IllegalArgumentException("initialCapacity is too large: " + initialCapacity);
        capacity = MathHelper.nextPowerOfTwo(initialCapacity);

        if (loadFactor <= 0)
            throw new IllegalArgumentException("loadFactor must be > 0: " + loadFactor);
        this.loadFactor = loadFactor;

        // big table is when capacity >= 2^16
        isBigTable = (capacity >>> 16) != 0;

        threshold = (int) (capacity * loadFactor);
        mask = capacity - 1;
        hashShift = 31 - Integer.numberOfTrailingZeros(capacity);
        stashCapacity = Math.max(3, (int) Math.ceil(Math.log(capacity)) * 2);
        pushIterations = Math.max(Math.min(capacity, 8), (int) Math.sqrt(capacity) / 8);

        keyTable = new int[capacity + stashCapacity];
        valueTable = (V[]) new Object[keyTable.length];
    }

    /**
     * Creates a new map identical to the specified map.
     */
    public IntHashMap(IntHashMap<? extends V> map) {
        this(map.capacity, map.loadFactor);
        stashSize = map.stashSize;
        System.arraycopy(map.keyTable, 0, keyTable, 0, map.keyTable.length);
        System.arraycopy(map.valueTable, 0, valueTable, 0, map.valueTable.length);
        size = map.size;
        zeroValue = map.zeroValue;
        hasZeroValue = map.hasZeroValue;
    }

    @Override
    public IntHashMap<V> cloneInstance() {
        IntHashMap<V> map = new IntHashMap<>(size());
        this.forEachEntry((value, index) -> {
            map.put(index, value);
        });
        return map;
    }

    public V put(int key, V value) {
        if (value == null)
            return remove(key);

        if (key == 0) {
            V oldValue = zeroValue;
            zeroValue = value;
            if (!hasZeroValue) {
                hasZeroValue = true;
                size++;
            }
            return oldValue;
        }

        // avoid getfield opcode
        int[] keyTable = this.keyTable;
        int mask = this.mask;
        boolean isBigTable = this.isBigTable;

        // Check for existing keys.
        int index1 = key & mask;
        int key1 = keyTable[index1];
        if (key1 == key) {
            V oldValue = valueTable[index1];
            valueTable[index1] = value;
            return oldValue;
        }

        int index2 = hash2(key);
        int key2 = keyTable[index2];
        if (key2 == key) {
            V oldValue = valueTable[index2];
            valueTable[index2] = value;
            return oldValue;
        }

        int index3 = hash3(key);
        int key3 = keyTable[index3];
        if (key3 == key) {
            V oldValue = valueTable[index3];
            valueTable[index3] = value;
            return oldValue;
        }

        int index4 = -1;
        int key4 = -1;
        if (isBigTable) {
            index4 = hash4(key);
            key4 = keyTable[index4];
            if (key4 == key) {
                V oldValue = valueTable[index4];
                valueTable[index4] = value;
                return oldValue;
            }
        }

        // Update key in the stash.
        for (int i = capacity, n = i + stashSize; i < n; i++) {
            if (keyTable[i] == key) {
                V oldValue = valueTable[i];
                valueTable[i] = value;
                return oldValue;
            }
        }

        // Check for empty buckets.
        if (key1 == EMPTY) {
            keyTable[index1] = key;
            valueTable[index1] = value;
            if (size++ >= threshold)
                resize(capacity << 1);
            return null;
        }

        if (key2 == EMPTY) {
            keyTable[index2] = key;
            valueTable[index2] = value;
            if (size++ >= threshold)
                resize(capacity << 1);
            return null;
        }

        if (key3 == EMPTY) {
            keyTable[index3] = key;
            valueTable[index3] = value;
            if (size++ >= threshold)
                resize(capacity << 1);
            return null;
        }

        if (isBigTable && key4 == EMPTY) {
            keyTable[index4] = key;
            valueTable[index4] = value;
            if (size++ >= threshold)
                resize(capacity << 1);
            return null;
        }

        push(key, value, index1, key1, index2, key2, index3, key3, index4, key4);

        return null;
    }

    public void putAll(IntHashMap<V> map) {
        for (Entry<V> entry : map.entries())
            put(entry.key, entry.value);
    }

    /**
     * Skips checks for existing keys.
     */
    private void putResize(int key, V value) {
        if (key == 0) {
            zeroValue = value;
            hasZeroValue = true;
            return;
        }

        // Check for empty buckets.
        int index1 = key & mask;
        int key1 = keyTable[index1];
        if (key1 == EMPTY) {
            keyTable[index1] = key;
            valueTable[index1] = value;
            if (size++ >= threshold)
                resize(capacity << 1);
            return;
        }

        int index2 = hash2(key);
        int key2 = keyTable[index2];
        if (key2 == EMPTY) {
            keyTable[index2] = key;
            valueTable[index2] = value;
            if (size++ >= threshold)
                resize(capacity << 1);
            return;
        }

        int index3 = hash3(key);
        int key3 = keyTable[index3];
        if (key3 == EMPTY) {
            keyTable[index3] = key;
            valueTable[index3] = value;
            if (size++ >= threshold)
                resize(capacity << 1);
            return;
        }

        int index4 = -1;
        int key4 = -1;
        if (isBigTable) {
            index4 = hash4(key);
            key4 = keyTable[index4];
            if (key4 == EMPTY) {
                keyTable[index4] = key;
                valueTable[index4] = value;
                if (size++ >= threshold)
                    resize(capacity << 1);
                return;
            }
        }

        push(key, value, index1, key1, index2, key2, index3, key3, index4, key4);
    }

    private void push(int insertKey, V insertValue, int index1, int key1, int index2, int key2, int index3, int key3,
                      int index4, int key4) {
        // avoid getfield opcode
        int[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        int mask = this.mask;
        boolean isBigTable = this.isBigTable;

        // Push keys until an empty bucket is found.
        int evictedKey;
        V evictedValue;
        int i = 0, pushIterations = this.pushIterations;
        int n = isBigTable ? 4 : 3;
        do {
            // Replace the key and value for one of the hashes.
            switch (MathHelper.random().nextInt(n)) {
                case 0:
                    evictedKey = key1;
                    evictedValue = valueTable[index1];
                    keyTable[index1] = insertKey;
                    valueTable[index1] = insertValue;
                    break;
                case 1:
                    evictedKey = key2;
                    evictedValue = valueTable[index2];
                    keyTable[index2] = insertKey;
                    valueTable[index2] = insertValue;
                    break;
                case 2:
                    evictedKey = key3;
                    evictedValue = valueTable[index3];
                    keyTable[index3] = insertKey;
                    valueTable[index3] = insertValue;
                    break;
                default:
                    evictedKey = key4;
                    evictedValue = valueTable[index4];
                    keyTable[index4] = insertKey;
                    valueTable[index4] = insertValue;
                    break;
            }

            // If the evicted key hashes to an empty bucket, put it there and stop.
            index1 = evictedKey & mask;
            key1 = keyTable[index1];
            if (key1 == EMPTY) {
                keyTable[index1] = evictedKey;
                valueTable[index1] = evictedValue;
                if (size++ >= threshold)
                    resize(capacity << 1);
                return;
            }

            index2 = hash2(evictedKey);
            key2 = keyTable[index2];
            if (key2 == EMPTY) {
                keyTable[index2] = evictedKey;
                valueTable[index2] = evictedValue;
                if (size++ >= threshold)
                    resize(capacity << 1);
                return;
            }

            index3 = hash3(evictedKey);
            key3 = keyTable[index3];
            if (key3 == EMPTY) {
                keyTable[index3] = evictedKey;
                valueTable[index3] = evictedValue;
                if (size++ >= threshold)
                    resize(capacity << 1);
                return;
            }

            if (isBigTable) {
                index4 = hash4(evictedKey);
                key4 = keyTable[index4];
                if (key4 == EMPTY) {
                    keyTable[index4] = evictedKey;
                    valueTable[index4] = evictedValue;
                    if (size++ >= threshold)
                        resize(capacity << 1);
                    return;
                }
            }

            if (++i == pushIterations)
                break;

            insertKey = evictedKey;
            insertValue = evictedValue;
        } while (true);

        putStash(evictedKey, evictedValue);
    }

    private void putStash(int key, V value) {
        if (stashSize == stashCapacity) {
            // Too many pushes occurred and the stash is full, increase the table size.
            resize(capacity << 1);
            put(key, value);
            return;
        }
        // Store key in the stash.
        int index = capacity + stashSize;
        keyTable[index] = key;
        valueTable[index] = value;
        stashSize++;
        size++;
    }

    public V get(int key) {
        if (key == 0) {
            if (!hasZeroValue)
                return null;
            return zeroValue;
        }
        int index = key & mask;
        if (keyTable[index] != key) {
            index = hash2(key);
            if (keyTable[index] != key) {
                index = hash3(key);
                if (keyTable[index] != key) {
                    if (isBigTable) {
                        index = hash4(key);
                        if (keyTable[index] != key)
                            return getStash(key, null);
                    } else {
                        return getStash(key, null);
                    }
                }
            }
        }
        return valueTable[index];
    }

    public V computeIfAbsent(int key, IntFunction<V> fn) {
        if (key == 0) {
            if (!hasZeroValue) {
                zeroValue = fn.apply(0);
                Guard.notNull(zeroValue, "zeroValue");

                hasZeroValue = true;
                size++;
                return zeroValue;
            }
            return zeroValue;
        }
        int index = key & mask;
        if (keyTable[index] != key) {
            index = hash2(key);
            if (keyTable[index] != key) {
                index = hash3(key);
                if (keyTable[index] != key) {
                    if (isBigTable) {
                        index = hash4(key);
                        if (keyTable[index] != key) {
                            V obj = getStash(key, null);
                            if (obj == null) {
                                obj = fn.apply(key);
                                put(key, obj);
                            }
                            return obj;
                        }
                    } else {
                        V obj = getStash(key, null);
                        if (obj == null) {
                            obj = fn.apply(key);
                            put(key, obj);
                        }
                        return obj;
                    }
                }
            }
        }
        V obj = valueTable[index];
        if (obj == null) {
            valueTable[index] = obj = fn.apply(key);
            Guard.notNull(obj, "computedValue");
            size++;
        }
        return obj;
    }

    public V get(int key, V defaultValue) {
        if (key == 0) {
            if (!hasZeroValue)
                return defaultValue;
            return zeroValue;
        }
        int index = key & mask;
        if (keyTable[index] != key) {
            index = hash2(key);
            if (keyTable[index] != key) {
                index = hash3(key);
                if (keyTable[index] != key) {
                    if (isBigTable) {
                        index = hash4(key);
                        if (keyTable[index] != key)
                            return getStash(key, defaultValue);
                    } else {
                        return getStash(key, defaultValue);
                    }
                }
            }
        }
        return valueTable[index];
    }

    private V getStash(int key, V defaultValue) {
        int[] keyTable = this.keyTable;
        for (int i = capacity, n = i + stashSize; i < n; i++)
            if (keyTable[i] == key)
                return valueTable[i];
        return defaultValue;
    }

    public V remove(int key) {
        if (key == 0) {
            if (!hasZeroValue)
                return null;
            V oldValue = zeroValue;
            zeroValue = null;
            hasZeroValue = false;
            size--;
            return oldValue;
        }

        int index = key & mask;
        if (keyTable[index] == key) {
            keyTable[index] = EMPTY;
            V oldValue = valueTable[index];
            valueTable[index] = null;
            size--;
            return oldValue;
        }

        index = hash2(key);
        if (keyTable[index] == key) {
            keyTable[index] = EMPTY;
            V oldValue = valueTable[index];
            valueTable[index] = null;
            size--;
            return oldValue;
        }

        index = hash3(key);
        if (keyTable[index] == key) {
            keyTable[index] = EMPTY;
            V oldValue = valueTable[index];
            valueTable[index] = null;
            size--;
            return oldValue;
        }

        if (isBigTable) {
            index = hash4(key);
            if (keyTable[index] == key) {
                keyTable[index] = EMPTY;
                V oldValue = valueTable[index];
                valueTable[index] = null;
                size--;
                return oldValue;
            }
        }

        return removeStash(key);
    }

    V removeStash(int key) {
        int[] keyTable = this.keyTable;
        for (int i = capacity, n = i + stashSize; i < n; i++) {
            if (keyTable[i] == key) {
                V oldValue = valueTable[i];
                removeStashIndex(i);
                size--;
                return oldValue;
            }
        }
        return null;
    }

    void removeStashIndex(int index) {
        // If the removed location was not last, move the last tuple to the removed location.
        stashSize--;
        int lastIndex = capacity + stashSize;
        if (index < lastIndex) {
            keyTable[index] = keyTable[lastIndex];
            valueTable[index] = valueTable[lastIndex];
            valueTable[lastIndex] = null;
            keyTable[lastIndex] = EMPTY;
        } else {
            keyTable[index] = EMPTY;
            valueTable[index] = null;
        }

        // checkValid();
    }

    /**
     * Reduces the size of the backing arrays to be the specified capacity or less. If the capacity is already less,
     * nothing is done. If the map contains more items than the specified capacity, the next highest power of two
     * capacity is used instead.
     */
    public void shrink(int maximumCapacity) {
        if (maximumCapacity < 0)
            throw new IllegalArgumentException("maximumCapacity must be >= 0: " + maximumCapacity);
        if (size > maximumCapacity)
            maximumCapacity = size;
        if (capacity <= maximumCapacity)
            return;
        maximumCapacity = MathHelper.nextPowerOfTwo(maximumCapacity);
        resize(maximumCapacity);
    }

    /**
     * Clears the map and reduces the size of the backing arrays to be the specified capacity if they are larger.
     */
    public void clear(int maximumCapacity) {
        if (capacity <= maximumCapacity) {
            clear();
            return;
        }
        zeroValue = null;
        hasZeroValue = false;
        size = 0;
        resize(maximumCapacity);
    }

    public void clear() {
        int[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        for (int i = capacity + stashSize; i-- > 0; ) {
            keyTable[i] = EMPTY;
            valueTable[i] = null;
        }
        size = 0;
        stashSize = 0;
        zeroValue = null;
        hasZeroValue = false;
    }

    /**
     * Returns true if the specified value is in the map. Note this traverses the entire map and compares every value,
     * which may be an expensive operation.
     *
     * @param identity If true, uses == to compare the specified value with values in the map. If false, uses
     *                 {@link #equals(Object)}.
     */
    public boolean containsValue(Object value, boolean identity) {
        V[] valueTable = this.valueTable;
        if (value == null) {
            if (hasZeroValue && zeroValue == null)
                return true;
            int[] keyTable = this.keyTable;
            for (int i = capacity + stashSize; i-- > 0; )
                if (keyTable[i] != EMPTY && valueTable[i] == null)
                    return true;
        } else if (identity) {
            if (value == zeroValue)
                return true;
            for (int i = capacity + stashSize; i-- > 0; )
                if (valueTable[i] == value)
                    return true;
        } else {
            if (hasZeroValue && value.equals(zeroValue))
                return true;
            for (int i = capacity + stashSize; i-- > 0; )
                if (value.equals(valueTable[i]))
                    return true;
        }
        return false;
    }

    public boolean containsKey(int key) {
        if (key == 0)
            return hasZeroValue;
        int index = key & mask;
        if (keyTable[index] != key) {
            index = hash2(key);
            if (keyTable[index] != key) {
                index = hash3(key);
                if (keyTable[index] != key) {
                    if (isBigTable) {
                        index = hash4(key);
                        if (keyTable[index] != key)
                            return containsKeyStash(key);
                    } else {
                        return containsKeyStash(key);
                    }
                }
            }
        }
        return true;
    }

    private boolean containsKeyStash(int key) {
        int[] keyTable = this.keyTable;
        for (int i = capacity, n = i + stashSize; i < n; i++)
            if (keyTable[i] == key)
                return true;
        return false;
    }

    /**
     * Returns the key for the specified value, or <tt>notFound</tt> if it is not in the map. Note this traverses the
     * entire map and compares every value, which may be an expensive operation.
     *
     * @param identity If true, uses == to compare the specified value with values in the map. If false, uses
     *                 {@link #equals(Object)}.
     */
    public int findKey(Object value, boolean identity, int notFound) {
        V[] valueTable = this.valueTable;
        if (value == null) {
            if (hasZeroValue && zeroValue == null)
                return 0;
            int[] keyTable = this.keyTable;
            for (int i = capacity + stashSize; i-- > 0; )
                if (keyTable[i] != EMPTY && valueTable[i] == null)
                    return keyTable[i];
        } else if (identity) {
            if (value == zeroValue)
                return 0;
            for (int i = capacity + stashSize; i-- > 0; )
                if (valueTable[i] == value)
                    return keyTable[i];
        } else {
            if (hasZeroValue && value.equals(zeroValue))
                return 0;
            for (int i = capacity + stashSize; i-- > 0; )
                if (value.equals(valueTable[i]))
                    return keyTable[i];
        }
        return notFound;
    }

    /**
     * Increases the size of the backing array to acommodate the specified number of additional items. Useful before
     * adding many items to avoid multiple backing array resizes.
     */
    public void ensureCapacity(int additionalCapacity) {
        int sizeNeeded = size + additionalCapacity;
        if (sizeNeeded >= threshold)
            resize(MathHelper.nextPowerOfTwo((int) (sizeNeeded / loadFactor)));
    }

    @SuppressWarnings("unchecked")
    private void resize(int newSize) {
        int oldEndIndex = capacity + stashSize;

        capacity = newSize;
        threshold = (int) (newSize * loadFactor);
        mask = newSize - 1;
        hashShift = 31 - Integer.numberOfTrailingZeros(newSize);
        stashCapacity = Math.max(3, (int) Math.ceil(Math.log(newSize)) * 2);
        pushIterations = Math.max(Math.min(newSize, 8), (int) Math.sqrt(newSize) / 8);

        // big table is when capacity >= 2^16
        isBigTable = (capacity >>> 16) != 0;

        int[] oldKeyTable = keyTable;
        V[] oldValueTable = valueTable;

        keyTable = new int[newSize + stashCapacity];
        valueTable = (V[]) new Object[newSize + stashCapacity];

        int oldSize = size;
        size = hasZeroValue ? 1 : 0;
        stashSize = 0;
        if (oldSize > 0) {
            for (int i = 0; i < oldEndIndex; i++) {
                int key = oldKeyTable[i];
                if (key != EMPTY)
                    putResize(key, oldValueTable[i]);
            }
        }
    }

    private int hash2(int h) {
        h *= PRIME2;
        return (h ^ h >>> hashShift) & mask;
    }

    private int hash3(int h) {
        h *= PRIME3;
        return (h ^ h >>> hashShift) & mask;
    }

    private int hash4(int h) {
        h *= PRIME4;
        return (h ^ h >>> hashShift) & mask;
    }

    public String toString() {
        if (size == 0)
            return "[]";
        StringBuilder buffer = new StringBuilder(32);
        buffer.append('[');
        int[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        int i = keyTable.length;
        if (hasZeroValue) {
            buffer.append("0=");
            buffer.append(zeroValue);
        } else {
            while (i-- > 0) {
                int key = keyTable[i];
                if (key != EMPTY) {
                    buffer.append(key);
                    buffer.append('=');
                    buffer.append(valueTable[i]);
                }
            }
        }
        while (i-- > 0) {
            int key = keyTable[i];
            if (key == EMPTY)
                continue;
            buffer.append(", ");
            buffer.append(key);
            buffer.append('=');
            buffer.append(valueTable[i]);
        }
        buffer.append(']');
        return buffer.toString();
    }

    @Override
    public void set(int index, V value) {
        put(index, value);
    }

    @Override
    public boolean remove(int index, V value) {
        V oldValue = get(index);
        if (oldValue == value) {
            remove(index);
            return true;
        }
        return false;
    }

    @Override
    public OfInt keysIterator() {
        return keys();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public IntArray keySet() {
        return keys().toArray();
    }

    @Override
    public boolean isEmpty() {
        return size() <= 0;
    }

    @Override
    public void forEachEntry(ObjIntConsumer<V> consumer) {
        if (hasZeroValue) {
            consumer.accept(zeroValue, 0);
        }
        if (size <= 0)
            return;

        for (int i = 0, n = keyTable.length; i < n; i++) {
            int key = keyTable[i];
            if (key != EMPTY) {
                consumer.accept(valueTable[i], key);
            }
        }
    }

    public void randomForEachEntry(ObjIntConsumer<V> consumer) {
        if (hasZeroValue) {
            consumer.accept(zeroValue, 0);
        }
        if (size <= 0)
            return;

        int startIndex = MathHelper.random().nextInt(keyTable.length);
        for (int i = 0, n = keyTable.length; i < n; i++) {
            int idx = (i + startIndex) % n;
            int key = keyTable[idx];
            if (key != EMPTY) {
                consumer.accept(valueTable[idx], key);
            }
        }
    }

    public void checkValid() {
        for (int i = 0, n = keyTable.length; i < n; i++) {
            if (keyTable[i] != EMPTY)
                Guard.checkArgument(valueTable[i] != null);
        }
    }

    public Entries<V> randomEntries() {
        if (size == 0)
            return entries();
        return new NavEntries<>(this, MathHelper.random().nextInt(keyTable.length));
    }

    /**
     * Returns an iterator for the entries in the map. Remove is supported. Note that the same iterator instance is
     * returned each time this method is called. Use the {@link Entries} constructor for nested or multithreaded
     * iteration.
     */
    public Entries<V> entries() {
        return new Entries<V>(this);
    }

    /**
     * Returns an iterator for the values in the map. Remove is supported. Note that the same iterator instance is
     * returned each time this method is called. Use the {@link Entries} constructor for nested or multithreaded
     * iteration.
     */
    public Values<V> values() {
        return new Values<V>(this);
    }

    /**
     * Returns an iterator for the keys in the map. Remove is supported. Note that the same iterator instance is
     * returned each time this method is called. Use the {@link Entries} constructor for nested or multithreaded
     * iteration.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Keys keys() {
        return new Keys((IntHashMap) this);
    }

    static public class Entry<V> {
        public int key;
        public V value;

        public String toString() {
            return key + "=" + value;
        }
    }

    static private class MapIterator<V> {
        static final int INDEX_ILLEGAL = -2;
        static final int INDEX_ZERO = -1;

        public boolean hasNext;

        final IntHashMap<V> map;
        int nextIndex, currentIndex;

        public MapIterator(IntHashMap<V> map, boolean reset) {
            this.map = map;
            if (reset)
                reset();
        }

        public MapIterator(IntHashMap<V> map) {
            this(map, true);
        }

        public void reset() {
            currentIndex = INDEX_ILLEGAL;
            nextIndex = INDEX_ZERO;
            if (map.hasZeroValue)
                hasNext = true;
            else
                findNextIndex();
        }

        public boolean hasNext() {
            return hasNext;
        }

        protected void findNextIndex() {
            hasNext = false;
            int[] keyTable = map.keyTable;
            for (int n = map.capacity + map.stashSize; ++nextIndex < n; ) {
                if (keyTable[nextIndex] != EMPTY) {
                    hasNext = true;
                    break;
                }
            }
        }

        public void remove() {
            if (currentIndex == INDEX_ZERO && map.hasZeroValue) {
                map.zeroValue = null;
                map.hasZeroValue = false;
            } else if (currentIndex < 0) {
                throw new IllegalStateException("next must be called before remove.");
            } else if (currentIndex >= map.capacity) {
                map.removeStashIndex(currentIndex);
                nextIndex = currentIndex - 1;
                findNextIndex();
            } else {
                map.keyTable[currentIndex] = EMPTY;
                map.valueTable[currentIndex] = null;
            }
            currentIndex = INDEX_ILLEGAL;
            map.size--;
        }
    }

    /**
     * 从指定index处开始遍历
     *
     * @param <V>
     */
    static class NavEntries<V> extends Entries<V> {
        private int count;
        private int startIndex;

        public NavEntries(IntHashMap<V> map, int startIndex) {
            super(map, true);
            this.startIndex = startIndex;
        }

        @Override
        protected void findNextIndex() {
            hasNext = false;
            if (nextIndex == INDEX_ZERO) {
                nextIndex = startIndex - 1;
            }

            int[] keyTable = map.keyTable;
            while (count < map.size) {
                nextIndex = (nextIndex + 1) % keyTable.length;
                if (keyTable[nextIndex] != EMPTY) {
                    count++;
                    hasNext = true;
                    break;
                }
            }
        }
    }

    static public class Entries<V> extends MapIterator<V> implements Iterable<Entry<V>>, Iterator<Entry<V>> {
        private Entry<V> entry = new Entry<V>();

        public Entries(IntHashMap<V> map, boolean reset) {
            super(map, reset);
        }

        public Entries(IntHashMap<V> map) {
            super(map, true);
        }

        /**
         * Note the same entry instance is returned each time this method is called.
         */
        public Entry<V> next() {
            if (!hasNext)
                throw new NoSuchElementException();
            int[] keyTable = map.keyTable;
            if (nextIndex == INDEX_ZERO) {
                entry.key = 0;
                entry.value = map.zeroValue;
            } else {
                entry.key = keyTable[nextIndex];
                entry.value = map.valueTable[nextIndex];
            }
            currentIndex = nextIndex;
            findNextIndex();
            return entry;
        }

        public boolean hasNext() {
            return hasNext;
        }

        public Iterator<Entry<V>> iterator() {
            return this;
        }
    }

    static public class Values<V> extends MapIterator<V> implements Iterable<V>, Iterator<V> {
        public Values(IntHashMap<V> map) {
            super(map);
        }

        public boolean hasNext() {
            return hasNext;
        }

        public V next() {
            if (!hasNext)
                throw new NoSuchElementException();
            V value;
            if (nextIndex == INDEX_ZERO)
                value = map.zeroValue;
            else
                value = map.valueTable[nextIndex];
            currentIndex = nextIndex;
            findNextIndex();
            return value;
        }

        public Iterator<V> iterator() {
            return this;
        }

        /**
         * Returns a new array containing the remaining values.
         */
        public ArrayList<V> toArray() {
            ArrayList<V> array = new ArrayList<V>(map.size);
            while (hasNext)
                array.add(next());
            return array;
        }
    }

    static public class Keys extends MapIterator<Object> implements OfInt {
        public Keys(IntHashMap<Object> map) {
            super(map);
        }

        public int nextInt() {
            if (!hasNext)
                throw new NoSuchElementException();
            int key = nextIndex == INDEX_ZERO ? 0 : map.keyTable[nextIndex];
            currentIndex = nextIndex;
            findNextIndex();
            return key;
        }

        /**
         * Returns a new array containing the remaining keys.
         */
        public IntArray toArray() {
            MutableIntArray array = new MutableIntArray(true, map.size);
            while (hasNext)
                array.add(nextInt());
            return array;
        }
    }
}
