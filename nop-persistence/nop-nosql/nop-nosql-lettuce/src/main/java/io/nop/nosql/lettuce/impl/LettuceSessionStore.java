/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.api.async.RedisAsyncCommands;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.functional.Functionals;
import io.nop.nosql.core.INosqlSessionStore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LettuceSessionStore extends AbstractLettuceOperations implements INosqlSessionStore {
    private final String prefix;

    public LettuceSessionStore(LettuceRedisConnectionProvider client, String prefix) {
        super(client);
        this.prefix = prefix;
    }

    private String sessionKey(String sessionId) {
        return prefix + ":" + sessionId;
    }

    @Override
    public CompletableFuture<Map<String, Object>> getAsync(String sessionId) {
        String key = sessionKey(sessionId);
        RedisAsyncCommands<String, Object> cmd = async();
        return cmd.hgetall(key)
                .<Map<String, Object>>thenApply(map -> {
                    if (map == null)
                        map = new HashMap<>();
                    return new HashMap<>(map);
                }).toCompletableFuture();
    }

    @Override
    public Map<String, Object> get(String sessionId) {
        return FutureHelper.syncGet(getAsync(sessionId));
    }

    @Override
    public CompletableFuture<Object> getFieldAsync(String sessionId, String field) {
        String key = sessionKey(sessionId);
        return async().hget(key, field).toCompletableFuture();
    }

    @Override
    public Object getField(String sessionId, String field) {
        return FutureHelper.syncGet(getFieldAsync(sessionId, field));
    }

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
        FutureHelper.syncGet(setAsync(sessionId, data, ttlMs));
    }

    @Override
    public CompletableFuture<Void> setFieldAsync(String sessionId, String field, Object value) {
        String key = sessionKey(sessionId);
        return async().hset(key, field, value)
                .thenApply(Functionals.toVoid())
                .toCompletableFuture();
    }

    @Override
    public void setField(String sessionId, String field, Object value) {
        FutureHelper.syncGet(setFieldAsync(sessionId, field, value));
    }

    @Override
    public CompletableFuture<Boolean> touchAsync(String sessionId, long ttlMs) {
        String key = sessionKey(sessionId);
        return async().pexpire(key, ttlMs).toCompletableFuture();
    }

    @Override
    public boolean touch(String sessionId, long ttlMs) {
        return FutureHelper.syncGet(touchAsync(sessionId, ttlMs));
    }

    @Override
    public CompletableFuture<Void> removeAsync(String sessionId) {
        String key = sessionKey(sessionId);
        return async().del(key)
                .thenApply(Functionals.toVoid())
                .toCompletableFuture();
    }

    @Override
    public void remove(String sessionId) {
        FutureHelper.syncGet(removeAsync(sessionId));
    }

    @Override
    public CompletableFuture<Boolean> existsAsync(String sessionId) {
        String key = sessionKey(sessionId);
        return async().exists(key)
                .thenApply(n -> n != null && n > 0)
                .toCompletableFuture();
    }

    @Override
    public boolean exists(String sessionId) {
        return FutureHelper.syncGet(existsAsync(sessionId));
    }
}
