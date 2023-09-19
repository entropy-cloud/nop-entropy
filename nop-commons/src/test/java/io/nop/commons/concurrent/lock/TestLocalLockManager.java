/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.lock;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.lock.impl.LocalResourceLockManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLocalLockManager {
    IScheduledExecutor timer;

    LocalResourceLockManager lockManager;

    @BeforeEach
    public void setUp() {
        lockManager = new LocalResourceLockManager();
        lockManager.setCleanupTimer(GlobalExecutors.globalTimer());
        lockManager.init();
    }

    @AfterEach
    public void tearDown() {
        if (lockManager != null)
            lockManager.destroy();
    }

    @Test
    public void testLock() {
        int n = 10;
        CountDownLatch latch = new CountDownLatch(n);

        CountDownLatch startLatch = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            final int index = i;
            final String holderId = "thread_" + i;
            new Thread() {
                public void run() {
                    startLatch.countDown();

                    try {
                        startLatch.await();
                    } catch (Exception e) {
                    }

                    IResourceLock lock = lockManager.getLock("a" + (index % 2), holderId);
                    for (int k = 0; k < 10; k++) {
                        try {
                            if (lock.tryLockWithLease(200L, 50)) {
                                try {
                                    Thread.sleep(100 + ThreadLocalRandom.current().nextInt(300));
                                } catch (Exception e) {
                                }
                                lock.unlock();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(50));
                        } catch (Exception e) {
                        }
                    }

                    latch.countDown();
                }
            }.start();
        }

        try {
            latch.await();
        } catch (Exception e) {
        }

        lockManager.dump();
        assertTrue(!lockManager.hasLock());
    }
}
