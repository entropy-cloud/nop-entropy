/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C2-003）载体钉：占位-完成模式的语义锚。
 * <p>
 * **结构钉（诚实登记）**：直接验证 {@code ResourceComponentManager.loadComponentModel} 使用的
 * **占位-完成模式**，而非端到端装配链（后者需要完整 ORM/DSL 环境与真实 component transform 注册）。
 * 两端：① 并发同 key 只构建一次（防「返回值身份判定」误写导致永久挂起）；② 构建失败传播异常而非挂起。
 * <p>
 * 与生产代码的同构性约束：本钉的 {@code boolean[]} + {@code computeIfAbsent} + {@code complete}
 * 三段必须与 {@code ResourceComponentManager.loadComponentModel} / {@code XLangSemantics.newInstance}
 * 保持完全一致；任一处改变形态需同步本钉。
 */
public class TestResourceComponentTransform {

    /** pin0203a：并发同 key 只执行一次 transform（计数桩），其余线程 join 同一 future。 */
    @Test
    public void pin0203a_concurrentSameKeyBuildsOnce() throws Exception {
        Map<String, CompletableFuture<Object>> map = new ConcurrentHashMap<>();
        AtomicInteger builds = new AtomicInteger();
        int n = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger results = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    // 与被修复代码同构：boolean[] 判定 + 占位 + 锁外构建
                    boolean[] created = new boolean[1];
                    CompletableFuture<Object> f = map.computeIfAbsent("k", k -> {
                        created[0] = true;
                        return new CompletableFuture<>();
                    });
                    if (created[0]) {
                        builds.incrementAndGet();
                        f.complete("built");
                    }
                    if ("built".equals(f.join()))
                        results.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));

        assertEquals(1, builds.get(), "并发同 key 只允许一次构建，实际=" + builds.get());
        assertEquals(n, results.get(), "所有线程都必须拿到结果（无人永久挂起）");
    }

    /** pin0203b：构建失败 → 后续调用得到异常而非挂起。 */
    @Test
    public void pin0203b_buildFailurePropagatesNotHangs() {
        Map<String, CompletableFuture<Object>> map = new ConcurrentHashMap<>();
        AtomicBoolean created = new AtomicBoolean();
        CompletableFuture<Object> f = map.computeIfAbsent("k", k -> {
            created.set(true);
            return new CompletableFuture<>();
        });
        assertTrue(created.get());
        f.completeExceptionally(new IllegalStateException("canned-transform-failure"));

        CompletableFuture<Object> same = map.get("k");
        assertEquals(f, same, "同一 key 必须复用同一占位 future");
        java.util.concurrent.CompletionException ce =
                assertThrows(java.util.concurrent.CompletionException.class, same::join);
        assertEquals("canned-transform-failure", ce.getCause().getMessage(),
                "失败必须传播为异常（而非永久挂起）");
    }
}
