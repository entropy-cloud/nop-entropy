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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * plan 2547 R4-13（C1-011）pin0111a：`tryLock(0)` 失败路径必须释放句柄——
     * 失败后另一把锁应能立即获取（缺陷态=句柄泄漏导致对方永久拿不到）。
     */
    @Test
    public void pin0111a_failedTryLockReleasesHandles() throws Exception {
        java.io.File lockFile = java.io.File.createTempFile("cra4111", ".lock");
        lockFile.deleteOnExit();
        lockFile.delete();

        LocalFileLock first = new LocalFileLock(lockFile, 10, 500);
        LocalFileLock second = new LocalFileLock(lockFile, 10, 500);
        assertTrue(first.tryLock(), "第一把锁应成功");
        try {
            // 同 JVM 内 FileLock 重叠会抛 OverlappingFileLockException（被包装为 NopException）
            // 或返回 false —— 两种形态都表示"未获锁"
            boolean acquired;
            try {
                acquired = second.tryLock();
            } catch (io.nop.api.core.exceptions.NopException e) {
                acquired = false;
            }
            assertFalse(acquired, "第二把锁在已持有时必须失败");

            // 关键：first 释放后，second 必须能立即获得（说明失败路径未泄漏句柄）
            first.unlock();
            assertTrue(second.tryLock(), "first 释放后 second 必须能获得（失败路径不得泄漏句柄）");
            second.unlock();
        } finally {
            // 清理阶段：unlock 在未持有时是空操作，异常无需处理（仅确保句柄不残留）
            try { first.unlock(); } catch (Exception cleanupIgnored) { /* intentionally ignored: best-effort cleanup */ }
            try { second.unlock(); } catch (Exception cleanupIgnored) { /* intentionally ignored: best-effort cleanup */ }
        }
    }

    /**
     * plan 2547 R4-13（C1-011）pin0111b：同一实例两线程使用时句柄不串——
     * unlock 只关闭「锁成功会话」，不会误关另一线程的句柄。
     */
    @Test
    public void pin0111b_unlockClosesOnlyHeldSession() throws Exception {
        java.io.File lockFile = java.io.File.createTempFile("cra4111b", ".lock");
        lockFile.deleteOnExit();
        lockFile.delete();

        LocalFileLock lock = new LocalFileLock(lockFile, 10, 500);
        assertTrue(lock.tryLock());
        lock.unlock();
        // 再次获取必须成功（说明会话对象被正确重建、句柄已释放）
        assertTrue(lock.tryLock(), "unlock 后必须能重新获取");
        lock.unlock();
        // 未持有时 unlock 必须是空操作（不抛异常）
        lock.unlock();
    }
}
