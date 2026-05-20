/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import java.util.concurrent.CompletableFuture;

public interface INosqlLock {
    CompletableFuture<Boolean> tryLockAsync(long leaseTimeMs);

    boolean tryLock(long leaseTimeMs);

    CompletableFuture<Void> unlockAsync();

    void unlock();

    boolean isHeld();
}
