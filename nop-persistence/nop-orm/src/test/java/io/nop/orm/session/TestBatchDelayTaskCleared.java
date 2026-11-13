/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.app.SimsCollege;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.persister.BatchActionQueueImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C3-003（plan 2553 裁定 G）钉：flush 完成后 BatchActionQueueImpl.delayTasks 字段必须被清空。
 * <p>通过 flush 窗口内 delay task 再入 enqueue（落新容器）构造「finally 清理时字段仍非 null」
 * 的确定性形态：finally 的 {@code this.delayTasks = null} 漏写（局部遮蔽变异）时字段残留非
 * null → 下次 flush 重放已消费任务 → 红。正常路径无再入时 swap-then-capture 已清字段，
 * 变异不可观察，故必须走再入腿。</p>
 */
public class TestBatchDelayTaskCleared extends AbstractOrmTestCase {

    @Test
    public void pin_delayTasksFieldClearedAfterFlushEvenWithReentrantEnqueue() throws Exception {
        // throws Exception：reflection Field 声明了 NoSuchFieldException/IllegalAccessException（测试侧反射钉）
        orm().runInSession(session -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege college = dao.loadEntityById("1");
            college.setCollegeName("delay-task-clear-pinned");

            AtomicBoolean firstTaskRun = new AtomicBoolean(false);
            BatchActionQueueImpl queue = (BatchActionQueueImpl) ((IOrmSessionImplementor) session)
                    .getBatchActionQueue(null);

            // 第一个延迟任务在 flush 成功收尾时执行；执行期间再入 enqueue 第二个任务，
            // 使 delayTasks 字段在 finally 清理时点非 null（变异可观察面）
            queue.addDelayTask("pin-first", () -> {
                firstTaskRun.set(true);
                queue.addDelayTask("pin-reentrant", () -> {
                });
            });

            session.flush();

            assertTrue(firstTaskRun.get(), "flush 成功后延迟任务必须被执行");

            // 变异红：恢复局部遮蔽（delayTasks = null 不带 this 前缀）后字段残留非 null → pin 红
            Object residual;
            Field field;
            try {
                field = BatchActionQueueImpl.class.getDeclaredField("delayTasks");
                field.setAccessible(true);
                residual = field.get(queue);
            } catch (ReflectiveOperationException e) {
                throw NopException.adapt(e);
            }
            assertNull(residual,
                    "flush 完成后 delayTasks 字段必须被清空（含 flush 窗口内再入 enqueue 的任务）");

            // 再入 enqueue 的任务不得跨 flush 重放：本 session 再次 flush 后仍应无残留
            session.flush();
            try {
                assertNull(field.get(queue), "重放 flush 后 delayTasks 字段不得残留已消费任务");
            } catch (IllegalAccessException e) {
                throw NopException.adapt(e);
            }
            return null;
        });
    }
}
