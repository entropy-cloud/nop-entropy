/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.app.SimsCollege;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2517 R2-14 红④（C2-001，C10-004 半边）回归钉：
 * T2 并发 flush 不再静默跳过——竞争线程锁内等待（互斥），T2 的 flush 返回时 T1 的 flush 必须已完成。
 * 缺陷态：T2 见 flusher != null 静默 return（事务带未刷写提交丢更新面）→ 红；
 * 修复后：session 锁内串行 → 绿。
 */
public class TestCra2517SessionMutex extends AbstractOrmTestCase {

    @Test
    public void testCra2517_C2_001_concurrentFlushWaitsInsteadOfSilentSkip() throws Exception {
        CountDownLatch t1InDelayTask = new CountDownLatch(1);
        CountDownLatch releaseT1 = new CountDownLatch(1);
        AtomicBoolean t1FlushDone = new AtomicBoolean(false);
        AtomicBoolean t2SawT1Done = new AtomicBoolean(false);
        AtomicReference<Throwable> t2Error = new AtomicReference<>();

        orm().runInSession((io.nop.orm.IOrmSession session) -> {
            // 主线程捕获 session 绑定的 context（T2 线程无 ThreadLocal context）
            io.nop.api.core.context.IContext boundContext = io.nop.api.core.context.ContextProvider.currentContext();
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege college = dao.loadEntityById("1");
            college.setCollegeName("cra2517-mutex");

            // T1 flush 挂起钩子：延迟任务在批 DML 成功后执行，阻塞 T1 的 flush 直至释放
            ((IOrmSessionImplementor) session).getBatchActionQueue(null).addDelayTask("cra2517-mutex", () -> {
                t1InDelayTask.countDown();
                try {
                    releaseT1.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                t1FlushDone.set(true);
            });

            Thread t2 = new Thread(() -> {
                try {
                    assertTrue(t1InDelayTask.await(5, TimeUnit.SECONDS), "T1 flush 未进入延迟任务相位");
                    // T2 在 context 上并发 flush（checkContext 对称：必须运行于 session 绑定的 context）
                    io.nop.api.core.context.IContext context = boundContext;
                    context.executeWithContext(() -> {
                        session.flush();
                        return null;
                    });
                    // T2 flush 返回时 T1 是否已完成（互斥绿判据）
                    t2SawT1Done.set(t1FlushDone.get());
                } catch (Throwable e) {
                    t2Error.set(e);
                }
            });
            t2.start();

            session.flush();

            try {
                t2.join(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertTrue(t2Error.get() == null, "T2 flush 异常: " + t2Error.get());
            assertTrue(t2SawT1Done.get(),
                    "缺陷态：T2 静默跳过 flush 提前返回（T1 未完成即返回）→ 红；修复后 T2 锁内等待 T1 完成 → 绿");
            return null;
        });
    }
}
