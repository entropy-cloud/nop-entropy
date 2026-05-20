/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.nop.commons.functional.Functionals;
import io.nop.nosql.core.INosqlSessionStore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LettuceSessionStore implements INosqlSessionStore {
    private final LettuceRedisConnectionProvider client;
    private final String prefix;

    public LettuceSessionStore(LettuceRedisConnectionProvider client, String prefix) {
        this.client = client;
        this.prefix = prefix;
    }

    protected RedisAdvancedClusterAsyncCommands<String, Object> async() {
        return client.getConnection().async();
    }

    protected RedisAdvancedClusterCommands<String, Object> sync() {
        return client.getConnection().sync();
    }

    private String sessionKey(String sessionId) {
        return prefix + ":" + sessionId;
    }

    // get: HGETALL
    @Override
    public CompletableFuture<Map<String, Object>> getAsync(String sessionId) {
        String key = sessionKey(sessionId);
        RedisAdvancedClusterAsyncCommands<String, Object> cmd = async();
        return cmd.hgetall(key)
                .thenApply((Map<String, Object> map) -> {
                    if (map == null)
                        map = new HashMap<>();
                    return (Map<String, Object>) new HashMap<>(map);
                }).toCompletableFuture();
    }

    @Override
    public Map<String, Object> get(String sessionId) {
        return getAsync(sessionId).join();
    }

    // getField: HGET
    @Override
    public CompletableFuture<Object> getFieldAsync(String sessionId, String field) {
        String key = sessionKey(sessionId);
        return async().hget(key, field).toCompletableFuture();
    }

    @Override
    public Object getField(String sessionId, String field) {
        return getFieldAsync(sessionId, field).join();
    }

    // set: HMSET + PEXPIRE (non-atomic, noted in design doc)
    @Override
    public CompletableFuture<Void> setAsync(String sessionId, Map<String, Object> data, long ttlMs) {
        String key = sessionKey(sessionId);
        return async().hmset(key, data)
                .thenCompose(v -> {
                    if (ttlMs > 0) {
                        return async().pexpire(key, ttlMs);
                    }
                    return CompletableFuture.completedFuture(true);
                })
                .thenApply(Functionals.toVoid())
                .toCompletableFuture();
    }

    @Override
    public void set(String sessionId, Map<String, Object> data, long ttlMs) {
        setAsync(sessionId, data, ttlMs).join();
    }

    // setField: HSET
    @Override
    public CompletableFuture<Void> setFieldAsync(String sessionId, String field, Object value) {
        String key = sessionKey(sessionId);
        return async().hset(key, field, value)
                .thenApply(Functionals.toVoid())
                .toCompletableFuture();
    }

    @Override
    public void setField(String sessionId, String field, Object value) {
        setFieldAsync(sessionId, field, value).join();
    }

    // touch: PEXPIRE (refresh TTL)
    @Override
    public CompletableFuture<Boolean> touchAsync(String sessionId, long ttlMs) {
        String key = sessionKey(sessionId);
        return async().pexpire(key, ttlMs).toCompletableFuture();
    }

    @Override
    public boolean touch(String sessionId, long ttlMs) {
        return touchAsync(sessionId, ttlMs).join();
    }

    // remove: DEL
    @Override
    public CompletableFuture<Void> removeAsync(String sessionId) {
        String key = sessionKey(sessionId);
        return async().del(key)
                .thenApply(Functionals.toVoid())
                .toCompletableFuture();
    }

    @Override
    public void remove(String sessionId) {
        removeAsync(sessionId).join();
    }

    // exists: EXISTS
    @Override
    public CompletableFuture<Boolean> existsAsync(String sessionId) {
        String key = sessionKey(sessionId);
        return async().exists(key)
                .thenApply(n -> n != null && n > 0)
                .toCompletableFuture();
    }

    @Override
    public boolean exists(String sessionId) {
        return existsAsync(sessionId).join();
    }
}
