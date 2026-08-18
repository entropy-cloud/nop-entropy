/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.api.core.message.IMessageService;
import io.nop.nosql.core.INosqlCounter;
import io.nop.nosql.core.INosqlHashOperations;
import io.nop.nosql.core.INosqlListOperations;
import io.nop.nosql.core.INosqlLock;
import io.nop.nosql.core.INosqlQueue;
import io.nop.nosql.core.INosqlRanking;
import io.nop.nosql.core.INosqlRateLimiter;
import io.nop.nosql.core.INosqlService;
import io.nop.nosql.core.INosqlSessionStore;
import io.nop.nosql.core.INosqlSetOperations;
import io.nop.nosql.core.INosqlZSetOperations;
import io.nop.nosql.core.RateLimiterConfig;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Test-only in-memory {@link INosqlService} that exercises the Redis store control flow
 * (putExAsync/get/removeIfMatch/counter.increment/setTimeoutAsync) without a real Redis.
 * <p>
 * Used to provide concrete wiring evidence (Plan Phase 3 Exit Criteria "接线验证"):
 * the Redis store classes are driven through their full lifecycle against this fake, and
 * {@link #calls} records the primitive invocations so tests can assert the store delegates
 * to the correct nop-nosql primitives (e.g. peek uses {@code get}, not {@code getExAsync}).
 * <p>
 * Unused INosqlService operations throw {@link UnsupportedOperationException} — these
 * stores only touch the string-key + counter surface.
 */
public class FakeNosqlService implements INosqlService {

    static final class Slot {
        final Object value;
        final long expireAtMillis;

        Slot(Object value, long expireAtMillis) {
            this.value = value;
            this.expireAtMillis = expireAtMillis;
        }
    }

    final Map<String, Slot> store = new ConcurrentHashMap<>();
    final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    final Map<String, Long> counterTtl = new ConcurrentHashMap<>();
    final Map<String, Integer> calls = new ConcurrentHashMap<>();

    void record(String method) {
        calls.merge(method, 1, Integer::sum);
    }

    public int callCount(String method) {
        return calls.getOrDefault(method, 0);
    }

    private Object rawGet(String key) {
        Slot s = store.get(key);
        if (s == null)
            return null;
        if (s.expireAtMillis > 0 && System.currentTimeMillis() >= s.expireAtMillis) {
            store.remove(key);
            return null;
        }
        return s.value;
    }

    // ---- INosqlKeyValueOperations / IAsyncMap surface used by the stores ----

    @Override
    public CompletionStage<Void> putExAsync(String key, Object value, long timeout) {
        record("putExAsync");
        long expireAt = timeout > 0 ? System.currentTimeMillis() + timeout : 0L;
        store.put(key, new Slot(value, expireAt));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Object get(String key) {
        record("get");
        return rawGet(key);
    }

    @Override
    public CompletionStage<Object> getAsync(String key) {
        record("getAsync");
        return CompletableFuture.completedFuture(rawGet(key));
    }

    @Override
    public CompletionStage<Object> getExAsync(String key, long timeout) {
        // MUST NOT be called by peek (would refresh TTL). Record so tests can assert zero.
        record("getExAsync");
        return CompletableFuture.completedFuture(rawGet(key));
    }

    @Override
    public void remove(String key) {
        record("remove");
        store.remove(key);
    }

    @Override
    public CompletionStage<Void> removeAsync(String key) {
        remove(key);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean removeIfMatch(String key, Object object) {
        record("removeIfMatch");
        Slot s = store.get(key);
        if (s == null)
            return false;
        Object current = rawGet(key);
        if (current == null)
            return false;
        // Mimic Redis CAS: the stores pass the exact object they just read via get(),
        // so reference equality holds. SmsCodeEntry also overrides equals. This avoids
        // requiring a JSON provider (not initialized in plain unit tests).
        boolean match = current == object
                || (current != null && current.equals(object));
        if (match) {
            store.remove(key);
            return true;
        }
        return false;
    }

    @Override
    public CompletionStage<Boolean> removeIfMatchAsync(String key, Object object) {
        return CompletableFuture.completedFuture(removeIfMatch(key, object));
    }

    @Override
    public CompletionStage<Boolean> setTimeoutAsync(String key, long timeout) {
        record("setTimeoutAsync");
        Slot s = store.get(key);
        if (s != null && timeout > 0) {
            store.put(key, new Slot(s.value, System.currentTimeMillis() + timeout));
        }
        if (counters.containsKey(key) && timeout > 0) {
            counterTtl.put(key, System.currentTimeMillis() + timeout);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void put(String key, Object value) {
        record("put");
        store.put(key, new Slot(value, 0L));
    }

    @Override
    public CompletionStage<Void> putAsync(String key, Object value) {
        put(key, value);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean putIfAbsent(String key, Object value) {
        record("putIfAbsent");
        return store.putIfAbsent(key, new Slot(value, 0L)) == null;
    }

    @Override
    public CompletionStage<Boolean> putIfAbsentAsync(String key, Object value) {
        return CompletableFuture.completedFuture(putIfAbsent(key, value));
    }

    /**
     * SETNX+PX（操作级票键，设计 §3.3 markVerified 原子性）：仅当键不存在时写入并设定 TTL。
     */
    @Override
    public CompletionStage<Boolean> putIfAbsentExAsync(String key, Object value, long timeout) {
        record("putIfAbsentExAsync");
        if (rawGet(key) != null)
            return CompletableFuture.completedFuture(Boolean.FALSE);
        long expireAt = timeout > 0 ? System.currentTimeMillis() + timeout : 0L;
        store.putIfAbsent(key, new Slot(value, expireAt));
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    // ---- counter (方案 A 原子失败计数) ----

    @Override
    public INosqlCounter counter(String key) {
        record("counter");
        AtomicLong c = counters.computeIfAbsent(key, k -> new AtomicLong(0));
        return new INosqlCounter() {
            @Override
            public CompletableFuture<Long> incrementAsync(long delta) {
                record("counter.increment");
                return CompletableFuture.completedFuture(c.addAndGet(delta));
            }

            @Override
            public long increment(long delta) {
                record("counter.increment");
                return c.addAndGet(delta);
            }

            @Override
            public CompletableFuture<Long> getAsync() {
                return CompletableFuture.completedFuture(c.get());
            }

            @Override
            public long get() {
                return c.get();
            }

            @Override
            public CompletableFuture<Long> getAndIncrementAsync(long delta) {
                return CompletableFuture.completedFuture(c.getAndAdd(delta));
            }

            @Override
            public long getAndIncrement(long delta) {
                return c.getAndAdd(delta);
            }

            @Override
            public CompletableFuture<Void> resetAsync(long value) {
                c.set(value);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void reset(long value) {
                c.set(value);
            }

            @Override
            public CompletableFuture<Long> getAndResetAsync() {
                return CompletableFuture.completedFuture(c.getAndSet(0));
            }

            @Override
            public long getAndReset() {
                return c.getAndSet(0);
            }
        };
    }

    // ---- the rest: not used by MFA stores ----

    @Override
    public INosqlHashOperations hashOps(String key) {
        throw new UnsupportedOperationException("hashOps not used by MFA stores");
    }

    @Override
    public INosqlListOperations listOps(String key) {
        throw new UnsupportedOperationException("listOps not used by MFA stores");
    }

    @Override
    public INosqlSetOperations setOps(String key) {
        throw new UnsupportedOperationException("setOps not used by MFA stores");
    }

    @Override
    public INosqlZSetOperations zSetOps(String key) {
        throw new UnsupportedOperationException("zSetOps not used by MFA stores");
    }

    @Override
    public IMessageService getMessageService() {
        throw new UnsupportedOperationException("getMessageService not used by MFA stores");
    }

    @Override
    public INosqlQueue queue(String key) {
        throw new UnsupportedOperationException("queue not used by MFA stores");
    }

    @Override
    public INosqlLock lock(String key) {
        throw new UnsupportedOperationException("lock not used by MFA stores");
    }

    @Override
    public INosqlRateLimiter rateLimiter(String key, RateLimiterConfig config) {
        throw new UnsupportedOperationException("rateLimiter not used by MFA stores");
    }

    @Override
    public INosqlRanking ranking(String key) {
        throw new UnsupportedOperationException("ranking not used by MFA stores");
    }

    @Override
    public INosqlSessionStore sessionStore(String prefix) {
        throw new UnsupportedOperationException("sessionStore not used by MFA stores");
    }

    // ---- remaining IAsyncMap / INosqlKeyValueOperations: not used by MFA stores ----

    @Override
    public CompletionStage<Long> getSizeAsync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getSize() {
        throw new UnsupportedOperationException();
    }

    // putIfAbsentExAsync 已在上方实现（操作级票键 SETNX+PX）

    @Override
    public CompletionStage<String> putIfAbsentOrMatchExAsync(String key, String value, long timeout) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Object> getAndSetExAsync(String key, Object value, long timeout) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Long> getTimeoutAsync(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object computeIfAbsent(String key, Function<? super String, ?> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Object> computeIfAbsentAsync(String key, Function<? super String, ?> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> getAll(Collection<? extends String> keys) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Map<String, Object>> getAllAsync(Collection<? extends String> keys) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Boolean> containsKeyAsync(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Void> putAllAsync(Map<? extends String, ?> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getAndSet(String key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Object> getAndSetAsync(String key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeAll(Collection<? extends String> keys) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Void> removeAllAsync(Collection<? extends String> keys) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        store.clear();
        counters.clear();
    }

    @Override
    public CompletionStage<Void> clearAsync() {
        clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void forEachEntry(BiConsumer<? super String, ? super Object> consumer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Void> forEachEntryAsync(BiConsumer<? super String, ? super Object> consumer) {
        throw new UnsupportedOperationException();
    }
}
