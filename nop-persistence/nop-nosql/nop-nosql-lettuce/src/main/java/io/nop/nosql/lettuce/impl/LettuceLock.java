/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.nop.nosql.core.INosqlLock;
import io.nop.nosql.core.script.RedisScripts;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LettuceLock implements INosqlLock {
    private final LettuceRedisConnectionProvider client;
    private final String key;
    private volatile String lockValue;

    public LettuceLock(LettuceRedisConnectionProvider client, String key) {
        this.client = client;
        this.key = key;
    }

    protected RedisAdvancedClusterAsyncCommands<String, Object> async() {
        return client.getConnection().async();
    }

    protected RedisAdvancedClusterCommands<String, Object> sync() {
        return client.getConnection().sync();
    }

    @Override
    public CompletableFuture<Boolean> tryLockAsync(long leaseTimeMs) {
        String uuid = UUID.randomUUID().toString();
        SetArgs args = new SetArgs().nx().px(leaseTimeMs);
        return async().set(key, uuid, args).thenApply(s -> {
            if ("OK".equals(s)) {
                lockValue = uuid;
                return true;
            }
            return false;
        }).toCompletableFuture();
    }

    @Override
    public boolean tryLock(long leaseTimeMs) {
        return tryLockAsync(leaseTimeMs).join();
    }

    @Override
    public CompletableFuture<Void> unlockAsync() {
        String value = lockValue;
        if (value == null) {
            return CompletableFuture.completedFuture(null);
        }
        return LettuceExecutor.evalScript(async(), RedisScripts.REMOVE_IF_MATCH,
                ScriptOutputType.BOOLEAN, new String[]{key}, new Object[]{value}
        ).thenAccept(removed -> lockValue = null).toCompletableFuture();
    }

    @Override
    public void unlock() {
        unlockAsync().join();
    }

    @Override
    public boolean isHeld() {
        return lockValue != null;
    }
}
