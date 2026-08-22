/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLocalFileLock {

    @TempDir
    File tempDir;

    @Test
    public void testLockAcquireSuccess() {
        File lockFile = new File(tempDir, "a.lock");
        LocalFileLock lock = new LocalFileLock(lockFile, 10, 2000);

        // 获取锁成功时lock()不应抛出超时异常
        assertDoesNotThrow(lock::lock);
        lock.unlock();

        // 获取锁成功时tryLock()应返回true
        assertTrue(lock.tryLock());
        lock.unlock();

        // 解锁后可以再次获取
        assertDoesNotThrow(lock::lock);
        lock.unlock();
    }
}
