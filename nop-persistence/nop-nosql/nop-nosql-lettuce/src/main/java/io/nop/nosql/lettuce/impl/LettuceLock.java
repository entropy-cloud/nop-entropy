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
import io.nop.api.core.util.FutureHelper;
import io.nop.nosql.core.INosqlLock;
import io.nop.nosql.core.script.RedisScripts;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A Redis-based distributed lock using SET NX PX and Lua CAS for unlock.
 * This class is intended for single-thread use per instance.
 * Concurrent tryLock/unlock calls on the same instance from different threads is undefined behavior.
 */
public class LettuceLock extends AbstractLettuceOperations implements INosqlLock {
    private final String key;
    private volatile String lockValue;

    public LettuceLock(LettuceRedisConnectionProvider client, String key) {
        super(client);
        this.key = key;
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
        return FutureHelper.syncGet(tryLockAsync(leaseTimeMs));
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
        FutureHelper.syncGet(unlockAsync());
    }

    @Override
    public boolean isHeld() {
        return lockValue != null;
    }
}
