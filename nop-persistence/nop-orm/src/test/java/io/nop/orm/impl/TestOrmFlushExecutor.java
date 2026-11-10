/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.app.SimsCollege;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C4-002（plan 2553 裁定 J）钉：flush 载体隔离——finalize I/O 落注入执行器。
 * <p>注入 flushExecutor 后，runInNewSessionAsync 的 finalize 任务（含 session.flush 的 JDBC I/O）
 * 必须整体经注入执行器投递；缺省 null = 调用线程现行语义。变异红：dispatchSessionFinalize
 * 忽略注入执行器（直接内联/context 路径）→ intercepted 标志为 false → 红。</p>
 */
public class TestOrmFlushExecutor extends AbstractOrmTestCase {

    @Test
    public void pin_flushIoRunsOnInjectedExecutor() {
        OrmTemplateImpl template = (OrmTemplateImpl) orm();

        AtomicBoolean intercepted = new AtomicBoolean(false);
        AtomicReference<String> executorThreadName = new AtomicReference<>();
        String markerName = "flush-executor-pinned";

        template.setFlushExecutor(task -> {
            intercepted.set(true);
            executorThreadName.set(Thread.currentThread().getName());
            task.run();
        });
        try {
            CompletionStage<Void> stage = template.runInNewSessionAsync(session -> {
                SimsCollege college = (SimsCollege) session.get(SimsCollege.class.getName(), "1");
                college.setCollegeName(markerName);
                return CompletableFuture.completedStage(null);
            });

            // 注入执行器内联执行 finalize，因此外层 stage 完成即代表 finalize 已跑完
            stage.toCompletableFuture().join();

            assertTrue(intercepted.get(), "finalize 任务必须经注入的 flushExecutor 投递执行");
            assertTrue(executorThreadName.get() != null, "执行器必须记录执行线程");

            // finalize 中的 session.flush 确实生效：新会话可读到落库值
            orm().runInSession(session -> {
                SimsCollege college = (SimsCollege) session.get(SimsCollege.class.getName(), "1");
                assertEquals(markerName, college.getCollegeName(),
                        "finalize flush 必须真实落库（注入执行器路径上的 I/O 不容静默丢失）");
                return null;
            });
        } finally {
            // setFlushExecutor 无 resetter 校验，setter 允许 null（volatile 字段直写），显式还原缺省语义
            template.setFlushExecutor(null);
        }
    }
}
