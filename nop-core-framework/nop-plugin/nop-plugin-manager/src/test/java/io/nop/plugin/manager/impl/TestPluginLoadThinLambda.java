/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.plugin.manager.impl;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2549 R4-15（C2-005）映射函数瘦身（bin 锁外构建）载体钉。
 */
public class TestPluginLoadThinLambda {

    /**
     * pin8b：同 id 双线程并发 loadPlugin——双检①（getIfPresent 无锁）后**两线程均进入锁外构建**
     * （latch(2) 双到达证明并行）。旧行为（computeIfAbsent 同 key）：第二线程阻塞在 bin 锁直至
     * 第一线程构建完成，到不了构建点 → latch 超时红。新行为立即绿（确定断言，非计时差）。
     * 失败方回收路径随 RuntimeException 走（构建失败可容忍——断言点是双到达）。
     */
    @Test
    public void pin8b_bothThreadsEnterBuildOutsideBinLock() throws Exception {
        PluginManagerImpl manager = new PluginManagerImpl();
        CountDownLatch bothInBuild = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        manager.setResourceResolver(coords -> {
            bothInBuild.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Collections.emptyList();
        });

        Runnable probe = () -> {
            try {
                manager.loadPlugin("io.nop.test:thin-probe:1.0.0");
            } catch (RuntimeException ignore) {
                // empty urls 构建/装载失败可容忍——断言点是双到达
            }
        };
        Thread threadA = new Thread(probe, "probe-a");
        Thread threadB = new Thread(probe, "probe-b");
        threadA.start();
        threadB.start();
        assertTrue(bothInBuild.await(5, TimeUnit.SECONDS),
                "缺陷态：第二线程阻塞在 CHM bin 锁内直至第一线程构建完成 → 无法并行进入构建点");
        release.countDown();
        threadA.join(10000);
        threadB.join(10000);
    }
}
