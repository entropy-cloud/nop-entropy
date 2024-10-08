/*
 * copy from Caffeine
 *
 * Copyright 2014 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.Serializable;

/**
 * Statistics about the performance of a {@link Cache}.
 * <p>
 * Cache statistics are incremented according to the following rules:
 * <ul>
 * <li>When a cache lookup encounters an existing cache entry {@code hitCount} is incremented.
 * <li>When a cache lookup first encounters a missing cache entry, a new entry is loaded.
 * <ul>
 * <li>After successfully loading an entry {@code missCount} and {@code loadSuccessCount} are incremented, and the total
 * loading time, in nanoseconds, is added to {@code totalLoadTime}.
 * <li>When an exception is thrown while loading an entry, {@code missCount} and {@code
 *         loadFailureCount} are incremented, and the total loading time, in nanoseconds, is added to
 * {@code totalLoadTime}.
 * <li>Cache lookups that encounter a missing cache entry that is still loading will wait for loading to complete
 * (whether successful or not) and then increment {@code missCount}.
 * </ul>
 * <li>When an entry is computed through the {@linkplain Cache#asMap asMap} the {@code loadSuccessCount} or
 * {@code loadFailureCount} is incremented.
 * <li>When an entry is evicted from the cache, {@code evictionCount} is incremented and the weight added to
 * {@code evictionWeight}.
 * <li>No stats are modified when a cache entry is invalidated or manually removed.
 * <li>No stats are modified by non-computing operations invoked on the {@linkplain Cache#asMap asMap} view of the
 * cache.
 * </ul>
 * <p>
 * A lookup is specifically defined as an invocation of one of the methods {@link LoadingCache#get(Object)},
 * {@link Cache#get(Object, java.util.function.Function)}, or {@link LoadingCache#getAll(Iterable)}.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class CacheStats implements Serializable {
    private static final long serialVersionUID = -2802626144795228544L;

    private static final CacheStats EMPTY_STATS = new CacheStats(0, 0, 0, 0, 0, 0, 0, 0, 0);

    private long hitCount;
    private long missCount;
    private long loadSuccessCount;
    private long loadFailureCount;
    private long totalLoadTime;
    private long evictionCount;
    private long evictionWeight;
    private long cacheGets;
    private long cachePuts;

    public CacheStats() {
    }

    /**
     * Constructs a new {@code CacheStats} instance.
     * <p>
     * Many parameters of the same type in a row is a bad thing, but this class is not constructed by end users and is
     * too fine-grained for a builder.
     *
     * @param hitCount         the number of cache hits
     * @param missCount        the number of cache misses
     * @param loadSuccessCount the number of successful cache loads
     * @param loadFailureCount the number of failed cache loads
     * @param totalLoadTime    the total load time (success and failure)
     * @param evictionCount    the number of entries evicted from the cache
     * @param evictionWeight   the sum of weights of entries evicted from the cache
     */
    public CacheStats(@NonNegative long hitCount, @NonNegative long missCount, @NonNegative long loadSuccessCount,
                      @NonNegative long loadFailureCount, @NonNegative long totalLoadTime, @NonNegative long evictionCount,
                      @NonNegative long evictionWeight, long cacheGets, long cachePuts) {
        if ((hitCount < 0) || (missCount < 0) || (loadSuccessCount < 0) || (loadFailureCount < 0) || (totalLoadTime < 0)
                || (evictionCount < 0) || (evictionWeight < 0)) {
            throw new IllegalArgumentException();
        }
        this.hitCount = hitCount;
        this.missCount = missCount;
        this.loadSuccessCount = loadSuccessCount;
        this.loadFailureCount = loadFailureCount;
        this.totalLoadTime = totalLoadTime;
        this.evictionCount = evictionCount;
        this.evictionWeight = evictionWeight;
        this.cacheGets = cacheGets;
        this.cachePuts = cachePuts;
    }

    public long getCacheGets() {
        return cacheGets;
    }

    public void setCacheGets(long cacheGets) {
        this.cacheGets = cacheGets;
    }

    public long getCachePuts() {
        return cachePuts;
    }

    public void setCachePuts(long cachePuts) {
        this.cachePuts = cachePuts;
    }

    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }

    public void setMissCount(long missCount) {
        this.missCount = missCount;
    }

    public void setLoadSuccessCount(long loadSuccessCount) {
        this.loadSuccessCount = loadSuccessCount;
    }

    public void setLoadFailureCount(long loadFailureCount) {
        this.loadFailureCount = loadFailureCount;
    }

    public void setTotalLoadTime(long totalLoadTime) {
        this.totalLoadTime = totalLoadTime;
    }

    public void setEvictionCount(long evictionCount) {
        this.evictionCount = evictionCount;
    }

    public void setEvictionWeight(long evictionWeight) {
        this.evictionWeight = evictionWeight;
    }

    /**
     * Returns a statistics instance where no cache events have been recorded.
     *
     * @return an empty statistics instance
     */
    @NonNull
    public static CacheStats empty() {
        return EMPTY_STATS;
    }

    /**
     * Returns the number of times {@link Cache} lookup methods have returned either a cached or uncached value. This is
     * defined as {@code hitCount + missCount}.
     *
     * @return the {@code hitCount + missCount}
     */
    @NonNegative
    public long requestCount() {
        return hitCount + missCount;
    }

    /**
     * Returns the number of times {@link Cache} lookup methods have returned a cached value.
     *
     * @return the number of times {@link Cache} lookup methods have returned a cached value
     */
    @NonNegative
    public long getHitCount() {
        return hitCount;
    }

    /**
     * Returns the ratio of cache requests which were hits. This is defined as {@code hitCount / requestCount}, or
     * {@code 1.0} when {@code requestCount == 0}. Note that {@code hitRate + missRate =~ 1.0}.
     *
     * @return the ratio of cache requests which were hits
     */
    @NonNegative
    public double hitRate() {
        long requestCount = requestCount();
        return (requestCount == 0) ? 1.0 : (double) hitCount / requestCount;
    }

    /**
     * Returns the number of times {@link Cache} lookup methods have returned an uncached (newly loaded) value, or null.
     * Multiple concurrent calls to {@link Cache} lookup methods on an absent value can result in multiple misses, all
     * returning the results of a single cache load operation.
     *
     * @return the number of times {@link Cache} lookup methods have returned an uncached (newly loaded) value, or null
     */
    @NonNegative
    public long getMissCount() {
        return missCount;
    }

    /**
     * Returns the ratio of cache requests which were misses. This is defined as {@code missCount / requestCount}, or
     * {@code 0.0} when {@code requestCount == 0}. Note that {@code hitRate + missRate =~ 1.0}. Cache misses include all
     * requests which weren't cache hits, including requests which resulted in either successful or failed loading
     * attempts, and requests which waited for other threads to finish loading. It is thus the case that
     * {@code missCount &gt;= loadSuccessCount + loadFailureCount}. Multiple concurrent misses for the same key will
     * result in a single load operation.
     *
     * @return the ratio of cache requests which were misses
     */
    @NonNegative
    public double missRate() {
        long requestCount = requestCount();
        return (requestCount == 0) ? 0.0 : (double) missCount / requestCount;
    }

    /**
     * Returns the total number of times that {@link Cache} lookup methods attempted to load new values. This includes
     * both successful load operations, as well as those that threw exceptions. This is defined as
     * {@code loadSuccessCount + loadFailureCount}.
     *
     * @return the {@code loadSuccessCount + loadFailureCount}
     */
    @NonNegative
    public long loadCount() {
        return loadSuccessCount + loadFailureCount;
    }

    /**
     * Returns the number of times {@link Cache} lookup methods have successfully loaded a new value. This is always
     * incremented in conjunction with {@link #missCount}, though {@code missCount} is also incremented when an
     * exception is encountered during cache loading (see {@link #loadFailureCount}). Multiple concurrent misses for the
     * same key will result in a single load operation.
     *
     * @return the number of times {@link Cache} lookup methods have successfully loaded a new value
     */
    @NonNegative
    public long getLoadSuccessCount() {
        return loadSuccessCount;
    }

    /**
     * Returns the number of times {@link Cache} lookup methods failed to load a new value, either because no value was
     * found or an exception was thrown while loading. This is always incremented in conjunction with {@code missCount},
     * though {@code missCount} is also incremented when cache loading completes successfully (see
     * {@link #loadSuccessCount}). Multiple concurrent misses for the same key will result in a single load operation.
     *
     * @return the number of times {@link Cache} lookup methods failed to load a new value
     */
    @NonNegative
    public long getLoadFailureCount() {
        return loadFailureCount;
    }

    /**
     * Returns the ratio of cache loading attempts which threw exceptions. This is defined as
     * {@code loadFailureCount / (loadSuccessCount + loadFailureCount)}, or {@code 0.0} when
     * {@code loadSuccessCount + loadFailureCount == 0}.
     *
     * @return the ratio of cache loading attempts which threw exceptions
     */
    @NonNegative
    public double loadFailureRate() {
        long totalLoadCount = loadSuccessCount + loadFailureCount;
        return (totalLoadCount == 0) ? 0.0 : (double) loadFailureCount / totalLoadCount;
    }

    /**
     * Returns the total number of nanoseconds the cache has spent loading new values. This can be used to calculate the
     * miss penalty. This value is increased every time {@code loadSuccessCount} or {@code loadFailureCount} is
     * incremented.
     *
     * @return the total number of nanoseconds the cache has spent loading new values
     */
    @NonNegative
    public long getTotalLoadTime() {
        return totalLoadTime;
    }

    /**
     * Returns the average time spent loading new values. This is defined as
     * {@code totalLoadTime / (loadSuccessCount + loadFailureCount)}.
     *
     * @return the average time spent loading new values
     */
    @NonNegative
    public double averageLoadPenalty() {
        long totalLoadCount = loadSuccessCount + loadFailureCount;
        return (totalLoadCount == 0) ? 0.0 : (double) totalLoadTime / totalLoadCount;
    }

    /**
     * Returns the number of times an entry has been evicted. This count does not include manual
     * {@linkplain Cache#invalidate invalidations}.
     *
     * @return the number of times an entry has been evicted
     */
    @NonNegative
    public long getEvictionCount() {
        return evictionCount;
    }

    /**
     * Returns the sum of weights of evicted entries. This total does not include manual {@linkplain Cache#invalidate
     * invalidations}.
     *
     * @return the sum of weights of evicted entities
     */
    @NonNegative
    public long getEvictionWeight() {
        return evictionWeight;
    }

    /**
     * Returns a new {@code CacheStats} representing the difference between this {@code CacheStats} and {@code other}.
     * Negative values, which aren't supported by {@code CacheStats} will be rounded up to zero.
     *
     * @param other the statistics to subtract with
     * @return the difference between this instance and {@code other}
     */
    @NonNull
    public CacheStats minus(@NonNull CacheStats other) {
        return new CacheStats(Math.max(0L, hitCount - other.hitCount), Math.max(0L, missCount - other.missCount),
                Math.max(0L, loadSuccessCount - other.loadSuccessCount),
                Math.max(0L, loadFailureCount - other.loadFailureCount),
                Math.max(0L, totalLoadTime - other.totalLoadTime), Math.max(0L, evictionCount - other.evictionCount),
                Math.max(0L, evictionWeight - other.evictionWeight),
                Math.max(0L, cacheGets - other.cacheGets), Math.max(0L, cachePuts - other.cachePuts));
    }

    /**
     * Returns a new {@code CacheStats} representing the sum of this {@code CacheStats} and {@code other}.
     *
     * @param other the statistics to add with
     * @return the sum of the statistics
     */
    @NonNull
    public CacheStats plus(@NonNull CacheStats other) {
        return new CacheStats(hitCount + other.hitCount, missCount + other.missCount,
                loadSuccessCount + other.loadSuccessCount, loadFailureCount + other.loadFailureCount,
                totalLoadTime + other.totalLoadTime, evictionCount + other.evictionCount,
                evictionWeight + other.evictionWeight, other.cacheGets, other.cachePuts);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '{' + "hitCount=" + hitCount + ", " + "missCount=" + missCount + ", "
                + "loadSuccessCount=" + loadSuccessCount + ", " + "loadFailureCount=" + loadFailureCount + ", "
                + "totalLoadTime=" + totalLoadTime + ", " + "evictionCount=" + evictionCount + ", " + "evictionWeight="
                + evictionWeight + '}';
    }
}