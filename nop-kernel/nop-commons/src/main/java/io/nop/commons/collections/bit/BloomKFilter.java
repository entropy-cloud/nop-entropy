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

import io.nop.commons.crypto.HashHelper;
import io.nop.commons.crypto.IHash64Function;

import java.util.Arrays;

/**
 * BloomKFilter is variation of {@link BloomFilter}. Unlike BloomFilter, BloomKFilter will spread 'k' hash bits within
 * same cache line for better L1 cache performance. The way it works is, First hash code is computed from key which is
 * used to locate the block offset (n-longs in bitset constitute a block) Subsequent 'k' hash codes are used to spread
 * hash bits within the block. By default block size is chosen as 8, which is to match cache line size (8 longs = 64
 * bytes = cache line size). Refer {@link BloomKFilter#add(T elm)} for more info.
 * <p>
 * This implementation has much lesser L1 data cache misses than {@link BloomFilter}.
 */
public class BloomKFilter<T> implements IFilterSet<T> {
    // static final float DEFAULT_FPP = 0.05f;
    private static final int DEFAULT_BLOCK_SIZE = 8;
    private static final int DEFAULT_BLOCK_SIZE_BITS = (int) (Math.log(DEFAULT_BLOCK_SIZE) / Math.log(2));
    private static final int DEFAULT_BLOCK_OFFSET_MASK = DEFAULT_BLOCK_SIZE - 1;
    private static final int DEFAULT_BIT_OFFSET_MASK = Long.SIZE - 1;
    private final long[] masks = new long[DEFAULT_BLOCK_SIZE];
    private final FixedBitSet bitSet;
    private final int m;
    private final int k;
    private final IHash64Function<T> hashFn;

    private int numberOfAddedElements;

    // spread k-1 bits to adjacent longs, default is 8
    // spreading hash bits within blockSize * longs will make bloom filter L1 cache friendly
    // default block size is set to 8 as most cache line sizes are 64 bytes and also AVX512 friendly
    private final int totalBlockCount;

    static void checkArgument(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    public BloomKFilter(long numBits, long maxNumEntries, IHash64Function<T> hashFn) {
        checkArgument(maxNumEntries > 0, "expectedEntries should be > 0");
        this.hashFn = hashFn;
        // long numBits = optimalNumOfBits(maxNumEntries, DEFAULT_FPP);
        this.k = BloomFilter.optimalNumOfHashFunctions(maxNumEntries, numBits);
        int nLongs = (int) Math.ceil((double) numBits / (double) Long.SIZE);
        // additional bits to pad long array to block size
        int padLongs = DEFAULT_BLOCK_SIZE - nLongs % DEFAULT_BLOCK_SIZE;
        this.m = (nLongs + padLongs) * Long.SIZE;
        this.bitSet = new FixedBitSet(m);
        checkArgument((bitSet.getDataLength() % DEFAULT_BLOCK_SIZE) == 0, "bitSet has to be block aligned");
        this.totalBlockCount = bitSet.getDataLength() / DEFAULT_BLOCK_SIZE;
    }

    /**
     * A constructor to support rebuilding the BloomFilter from a serialized representation.
     *
     * @param bits
     * @param numFuncs
     */
    public BloomKFilter(long[] bits, int numFuncs, int numberOfAddedElements, IHash64Function<T> hashFn) {
        this.hashFn = hashFn;
        this.numberOfAddedElements = numberOfAddedElements;
        bitSet = new FixedBitSet(bits);
        this.m = bits.length * Long.SIZE;
        this.k = numFuncs;
        checkArgument((bitSet.getDataLength() % DEFAULT_BLOCK_SIZE) == 0, "bitSet has to be block aligned");
        this.totalBlockCount = bitSet.getDataLength() / DEFAULT_BLOCK_SIZE;
    }

    public static BloomKFilter<String> forString(long inputEntries, double fpp) {
        int bitSetSize = BloomFilter.optimalNumOfBits(inputEntries, fpp);
        return new BloomKFilter<String>(bitSetSize, (int) inputEntries, HashHelper.murmur3_64_string());
    }

    public void add(T elm) {
        // We use the trick mentioned in "Less Hashing, Same Performance: Building a Better Bloom Filter"
        // by Kirsch et.al. From abstract 'only two hash functions are necessary to effectively
        // implement a Bloom filter without any loss in the asymptotic false positive probability'

        // Lets split up 64-bit hashcode into two 32-bit hash codes and employ the technique mentioned
        // in the above paper
        long hash64 = hashFn.hash64(elm);
        addHash(hash64);
    }

    private void addHash(long hash64) {
        final int hash1 = (int) hash64;
        final int hash2 = (int) (hash64 >>> 32);

        int firstHash = hash1 + hash2;
        // hashcode should be positive, flip all the bits if it's negative
        if (firstHash < 0) {
            firstHash = ~firstHash;
        }

        // first hash is used to locate start of the block (blockBaseOffset)
        // subsequent K hashes are used to generate K bits within a block of words
        final int blockIdx = firstHash % totalBlockCount;
        final int blockBaseOffset = blockIdx << DEFAULT_BLOCK_SIZE_BITS;
        long[] data = bitSet.getData();
        for (int i = 1; i <= k; i++) {
            int combinedHash = hash1 + ((i + 1) * hash2);
            // hashcode should be positive, flip all the bits if it's negative
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            // LSB 3 bits is used to locate offset within the block
            final int absOffset = blockBaseOffset + (combinedHash & DEFAULT_BLOCK_OFFSET_MASK);
            // Next 6 bits are used to locate offset within a long/word
            final int bitPos = (combinedHash >>> DEFAULT_BLOCK_SIZE_BITS) & DEFAULT_BIT_OFFSET_MASK;
            data[absOffset] |= (1L << bitPos);
        }
    }

    public boolean contains(T elm) {
        long hash64 = hashFn.hash64(elm);
        return containsHash(hash64);
    }

    private boolean containsHash(long hash64) {
        final int hash1 = (int) hash64;
        final int hash2 = (int) (hash64 >>> 32);

        int firstHash = hash1 + hash2;
        // hashcode should be positive, flip all the bits if it's negative
        if (firstHash < 0) {
            firstHash = ~firstHash;
        }

        // first hash is used to locate start of the block (blockBaseOffset)
        // subsequent K hashes are used to generate K bits within a block of words
        // To avoid branches during probe, a separate masks array is used for each longs/words within a block.
        // data array and masks array are then traversed together and checked for corresponding set bits.
        final int blockIdx = firstHash % totalBlockCount;
        final int blockBaseOffset = blockIdx << DEFAULT_BLOCK_SIZE_BITS;

        // iterate and update masks array
        for (int i = 1; i <= k; i++) {
            int combinedHash = hash1 + ((i + 1) * hash2);
            // hashcode should be positive, flip all the bits if it's negative
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            // LSB 3 bits is used to locate offset within the block
            final int wordOffset = combinedHash & DEFAULT_BLOCK_OFFSET_MASK;
            // Next 6 bits are used to locate offset within a long/word
            final int bitPos = (combinedHash >>> DEFAULT_BLOCK_SIZE_BITS) & DEFAULT_BIT_OFFSET_MASK;
            masks[wordOffset] |= (1L << bitPos);
        }

        long[] data = bitSet.getData();
        // traverse data and masks array together, check for set bits
        long expected = 0;
        for (int i = 0; i < DEFAULT_BLOCK_SIZE; i++) {
            final long mask = masks[i];
            expected |= (data[blockBaseOffset + i] & mask) ^ mask;
        }

        // clear the mask for array reuse (this is to avoid masks array allocation in inner loop)
        Arrays.fill(masks, 0);

        // if all bits are set, expected should be 0
        return expected == 0;
    }

    /**
     * Sets all bits to false in the Bloom filter.
     */
    public void clear() {
        bitSet.clear();
        numberOfAddedElements = 0;
    }

    @Override
    public boolean isEmpty() {
        return numberOfAddedElements == 0;
    }

    public int count() {
        return numberOfAddedElements;
    }

    public int getK() {
        return k;
    }

    public int getBizSetSize() {
        return m;
    }

}
