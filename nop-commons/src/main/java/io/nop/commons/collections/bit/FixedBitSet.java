/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.collections.bit;

import io.nop.api.core.util.Guard;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Bare metal bit set implementation. For performance reasons, this implementation does not check for index bounds nor
 * expand the bit set size if the specified index is greater than the size.
 */
public class FixedBitSet implements IBitSet, Serializable {
    private static final long serialVersionUID = -3124448971310339348L;
    private final long[] words;

    public FixedBitSet(int bits) {
        this(new long[(bits) + 1]);
    }

    /**
     * Deserialize long array as bit set.
     *
     * @param data - bit array
     */
    public FixedBitSet(long[] data) {
        assert data.length > 0 : "data length is zero!";
        this.words = data;
    }

    /**
     * Sets the bit at specified index.
     *
     * @param index - position
     */
    public void set(int index) {
        words[index >>> 6] |= (1L << index);
    }

    /**
     * Returns true if the bit is set in the specified index.
     *
     * @param index - position
     * @return - value at the bit position
     */
    public boolean get(int index) {
        return (words[index >>> 6] & (1L << index)) != 0;
    }

    /**
     * Number of bits
     */
    public int size() {
        return words.length * Long.SIZE;
    }

    public long[] getData() {
        return words;
    }

    public int getDataLength() {
        return words.length;
    }

    /**
     * Clear the bit set.
     */
    public void clear() {
        Arrays.fill(words, 0);
    }

    @Override
    public void set(int fromIndex, int toIndex) {
        if (fromIndex == toIndex)
            return;

        int startWordIndex = wordIndex(fromIndex);
        int endWordIndex = wordIndex(toIndex - 1);

        long firstWordMask = WORD_MASK << fromIndex;
        long lastWordMask = WORD_MASK >>> -toIndex;
        if (startWordIndex == endWordIndex) {
            words[startWordIndex] |= (firstWordMask & lastWordMask);
        } else {
            words[startWordIndex] |= firstWordMask;

            for (int i = startWordIndex + 1; i < endWordIndex; i++)
                words[i] = WORD_MASK;

            words[endWordIndex] |= lastWordMask;
        }
    }

    @Override
    public int count(int fromIndex, int toIndex) {
        int cnt = 0;
        for (int i = toIndex; (i = previousSetBit(i - 1)) >= fromIndex; ) {
            cnt++;
        }
        return cnt;
    }

    @Override
    public void clear(int bitIndex) {
        int wordIndex = wordIndex(bitIndex);
        words[wordIndex] &= ~(1L << bitIndex);
    }

    @Override
    public void clear(int fromIndex, int toIndex) {
        if (fromIndex == toIndex)
            return;

        int startWordIndex = wordIndex(fromIndex);
        int endWordIndex = wordIndex(toIndex - 1);

        long firstWordMask = WORD_MASK << fromIndex;
        long lastWordMask = WORD_MASK >>> -toIndex;
        if (startWordIndex == endWordIndex) {
            words[startWordIndex] &= ~(firstWordMask & lastWordMask);
        } else {
            words[startWordIndex] &= ~firstWordMask;

            for (int i = startWordIndex + 1; i < endWordIndex; i++)
                words[i] = 0;

            words[endWordIndex] &= ~lastWordMask;
        }

    }

    @Override
    public int cardinality() {
        int sum = 0;
        for (int i = 0, n = words.length; i < n; i++)
            sum += Long.bitCount(words[i]);
        return sum;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0, n = words.length; i < n; i++) {
            if (words[i] != 0)
                return false;
        }
        return true;
    }

    @Override
    public void flip(int bitIndex) {
        int wordIndex = wordIndex(bitIndex);
        words[wordIndex] ^= (1L << bitIndex);
    }

    @Override
    public void flip(int fromIndex, int toIndex) {
        if (fromIndex == toIndex)
            return;

        int startWordIndex = wordIndex(fromIndex);
        int endWordIndex = wordIndex(toIndex - 1);

        long firstWordMask = WORD_MASK << fromIndex;
        long lastWordMask = WORD_MASK >>> -toIndex;
        if (startWordIndex == endWordIndex) {
            words[startWordIndex] ^= (firstWordMask & lastWordMask);
        } else {
            words[startWordIndex] ^= firstWordMask;

            for (int i = startWordIndex + 1; i < endWordIndex; i++)
                words[i] ^= WORD_MASK;

            words[endWordIndex] ^= lastWordMask;
        }
    }

    @Override
    public void and(IBitSet bs) {
        if (this == bs)
            return;

        FixedBitSet set = toBitSet(bs);
        int wordsInUse = getDataLength();

        while (wordsInUse > set.getDataLength())
            words[--wordsInUse] = 0;

        for (int i = 0; i < wordsInUse; i++)
            words[i] &= set.words[i];
    }

    @Override
    public void andNot(IBitSet bs) {
        FixedBitSet set = toBitSet(bs);

        int wordsInUse = getDataLength();
        for (int i = Math.min(wordsInUse, set.getDataLength()) - 1; i >= 0; i--)
            words[i] &= ~set.words[i];
    }

    @Override
    public void or(IBitSet bs) {
        if (this == bs)
            return;

        FixedBitSet set = toBitSet(bs);
        int wordsInUse = getDataLength();
        int wordsInCommon = Math.min(wordsInUse, set.getDataLength());

        Guard.checkArgument(wordsInUse >= set.getDataLength(), "data length is too large");

        for (int i = 0; i < wordsInCommon; i++)
            words[i] |= set.words[i];

        if (wordsInCommon < set.getDataLength())
            System.arraycopy(set.words, wordsInCommon, words, wordsInCommon, set.getDataLength() - wordsInCommon);
    }

    @Override
    public void xor(IBitSet bs) {
        FixedBitSet set = toBitSet(bs);
        int wordsInUse = getDataLength();
        int wordsInCommon = Math.min(wordsInUse, set.getDataLength());

        Guard.checkArgument(wordsInUse >= set.getDataLength(), "data length is too large");

        for (int i = 0; i < wordsInCommon; i++)
            words[i] ^= set.words[i];

        if (wordsInCommon < set.getDataLength())
            System.arraycopy(set.words, wordsInCommon, words, wordsInCommon, set.getDataLength() - wordsInCommon);
    }

    FixedBitSet toBitSet(IBitSet bs) {
        return (FixedBitSet) bs;
    }

    @Override
    public boolean intersects(IBitSet bs) {
        FixedBitSet set = toBitSet(bs);

        for (int i = Math.min(words.length, set.getDataLength()) - 1; i >= 0; i--)
            if ((words[i] & set.words[i]) != 0)
                return true;
        return false;
    }

    private final static int ADDRESS_BITS_PER_WORD = 6;
    private final static int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
  //  private final static int BIT_INDEX_MASK = BITS_PER_WORD - 1;

    private static final long WORD_MASK = 0xffffffffffffffffL;

    private static int wordIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }

    public int previousSetBit(int fromIndex) {
        if (fromIndex < 0) {
            return -1;
        }

        int u = wordIndex(fromIndex);

        long word = words[u] & (WORD_MASK >>> -(fromIndex + 1));

        while (true) {
            if (word != 0)
                return (u + 1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(word);
            if (u-- == 0)
                return -1;
            word = words[u];
        }
    }

    @Override
    public int nextSetBit(int fromIndex) {
        // Guard.assertTrue(fromIndex >= 0, "collection.err_fromIndex_must_not_negative");

        int u = wordIndex(fromIndex);
        int wordsInUse = words.length;
        if (u >= wordsInUse)
            return -1;

        long word = words[u] & (WORD_MASK << fromIndex);

        while (true) {
            if (word != 0)
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
            if (++u == wordsInUse)
                return -1;
            word = words[u];
        }
    }

    @Override
    public int nextClearBit(int fromIndex) {
        // Guard.assertTrue(fromIndex >= 0, "collection.err_fromIndex_must_not_negative");

        int wordsInUse = words.length;
        int u = wordIndex(fromIndex);
        if (u >= wordsInUse)
            return fromIndex;

        long word = ~words[u] & (WORD_MASK << fromIndex);

        while (true) {
            if (word != 0)
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
            if (++u == wordsInUse)
                return wordsInUse * BITS_PER_WORD;
            word = ~words[u];
        }
    }

    @Override
    public IBitSet cloneInstance() {
        return new FixedBitSet(words.clone());
    }
}