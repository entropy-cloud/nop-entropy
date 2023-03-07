/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.commons.collections.bit;

// refactor from Hive BloomFilter

import io.nop.commons.crypto.HashHelper;
import io.nop.commons.crypto.IHash64Function;

public class BloomFilter<T> implements IFilterSet<T> {
    final IBitSet bitSet;
    final IHash64Function<T> hashFn;

    private final int bitSetSize;
    private final int expectedNumberOfElements; // expected (maximum) number of
    // elements to be added
    private int numberOfAddedElements; // number of elements actually added to
    // the Bloom filter
    private final int k; // number of hash functions

    /**
     * Constructs an empty Bloom filter. The optimal number of hash functions (k) is estimated from the total size of
     * the Bloom and the number of expected elements.
     *
     * @param bitSetSize               defines how many bits should be used in total for the filter.
     * @param expectedNumberOfElements defines the maximum number of elements the filter is expected to contain.
     */
    public BloomFilter(int bitSetSize, int expectedNumberOfElements, IHash64Function<T> hashFn, IBitSet bitSet,
                       int actualNumberOfElements) {
        this.bitSet = bitSet;
        this.hashFn = hashFn;
        this.bitSetSize = bitSetSize;
        this.expectedNumberOfElements = expectedNumberOfElements;
        this.numberOfAddedElements = actualNumberOfElements;
        this.k = optimalNumOfHashFunctions(expectedNumberOfElements, bitSetSize);
    }

    public BloomFilter(int bitSetSize, int expectedNumberOfElements, IHash64Function<T> hashFn, IBitSet bitSet) {
        this(bitSetSize, expectedNumberOfElements, hashFn, bitSet, 0);
    }

    public BloomFilter(int bitSetSize, int expectedNumberOfElements, IHash64Function<T> hashFn) {
        this(bitSetSize, expectedNumberOfElements, hashFn, new FixedBitSet(bitSetSize));
    }

    public static BloomFilter<String> forString(long inputEntries, double fpp) {
        int bitSetSize = optimalNumOfBits(inputEntries, fpp);
        return new BloomFilter<String>(bitSetSize, (int) inputEntries, HashHelper.murmur3_64_string());
    }

    /**
     * Compute optimal bits number with given input entries and expected false positive probability.
     *
     * @param inputEntries
     * @param fpp
     * @return optimal bits number
     */
    public static int optimalNumOfBits(long inputEntries, double fpp) {
        int numBits = (int) (-inputEntries * Math.log(fpp) / (Math.log(2) * Math.log(2)));
        return numBits;
    }

    /**
     * Compute the false positive probability based on given input entries and bits size. Note: this is just the math
     * expected value, you should not expect the fpp in real case would under the return value for certain.
     *
     * @param inputEntries 元素个数
     * @param bitSize      总比特数
     * @return
     */
    public static double estimateFalsePositiveProbability(long inputEntries, int bitSize) {
        int numFunction = optimalNumOfHashFunctions(inputEntries, bitSize);
        double p = Math.pow(Math.E, -(double) numFunction * inputEntries / bitSize);
        double estimatedFPP = Math.pow(1 - p, numFunction);
        return estimatedFPP;
    }

    /**
     * compute the optimal hash function number with given input entries and bits size, which would make the false
     * positive probability lowest.
     *
     * @param expectEntries 期望放入的元素个数
     * @param bitSetSize    比特数
     * @return hash function number
     */
    static int optimalNumOfHashFunctions(long expectEntries, long bitSetSize) {
        return Math.max(1, (int) Math.round((double) bitSetSize / expectEntries * Math.log(2)));
    }

    /**
     * Calculates the expected probability of false positives based on the number of expected filter elements and the
     * size of the Bloom filter. <br />
     * <br />
     * The value returned by this method is the <i>expected</i> rate of false positives, assuming the number of inserted
     * elements equals the number of expected elements. If the number of elements in the Bloom filter is less than the
     * expected value, the true probability of false positives will be lower.
     *
     * @return expected probability of false positives.
     */
    public double getExpectedFpp() {
        return _getFalsePositiveProbability(expectedNumberOfElements);
    }

    /**
     * Calculate the probability of a false positive given the specified number of inserted elements.
     *
     * @param numberOfElements number of inserted elements.
     * @return probability of a false positive.
     */
    private double _getFalsePositiveProbability(double numberOfElements) {
        // (1 - e^(-k * n / m)) ^ k
        return Math.pow((1 - Math.exp(-k * (double) numberOfElements / (double) bitSetSize)), k);

    }

    /**
     * Get the current probability of a false positive. The probability is calculated from the size of the Bloom filter
     * and the current number of elements added to it.
     *
     * @return probability of false positives.
     */
    public double getFpp() {
        return _getFalsePositiveProbability(numberOfAddedElements);
    }

    /**
     * Returns the value chosen for K.<br />
     * <br />
     * K is the optimal number of hash functions based on the size of the Bloom filter and the expected number of
     * inserted elements.
     *
     * @return optimal k.
     */
    public int getK() {
        return k;
    }

    /**
     * Sets all bits to false in the Bloom filter.
     */
    public void clear() {
        bitSet.clear();
        numberOfAddedElements = 0;
    }

    @Override
    public void add(T elm) {
        addHash(calcHash(elm));
        numberOfAddedElements++;
    }

    @Override
    public boolean isEmpty() {
        return numberOfAddedElements == 0;
    }

    public int getBitSetSize() {
        return bitSetSize;
    }

    public int count() {
        return numberOfAddedElements;
    }

    @Override
    public boolean contains(T elm) {
        return containsHash(calcHash(elm));
    }

    long calcHash(T elm) {
        return hashFn.hash64(elm);
    }

    // We use the trick mentioned in "Less Hashing, Same Performance: Building a Better Bloom Filter"
    // by Kirsch et.al. From abstract 'only two hash functions are necessary to effectively
    // implement a Bloom filter without any loss in the asymptotic false positive probability'
    // Lets split up 64-bit hashcode into two 32-bit hash codes and employ the technique mentioned
    // in the above paper
    private boolean containsHash(long hash64) {
        int hash1 = (int) hash64;
        int hash2 = (int) (hash64 >>> 32);

        for (int i = 1; i <= k; i++) {
            int combinedHash = hash1 + ((i + 1) * hash2);
            // hashcode should be positive, flip all the bits if it's negative
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            int pos = combinedHash % bitSetSize;
            if (!bitSet.get(pos)) {
                return false;
            }
        }
        return true;
    }

    private void addHash(long hash64) {
        int hash1 = (int) hash64;
        int hash2 = (int) (hash64 >>> 32);

        for (int i = 1; i <= k; i++) {
            int combinedHash = hash1 + ((i + 1) * hash2);
            // hashcode should be positive, flip all the bits if it's negative
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            int pos = combinedHash % bitSetSize;
            bitSet.set(pos);
        }
    }
}
