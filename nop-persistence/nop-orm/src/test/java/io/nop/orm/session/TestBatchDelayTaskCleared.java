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
 * 2.3-C3-003（plan 2553 裁定 G）钉 + plan 2587 裁定改写：flush 重入 enqueue「移交下一轮」。
 *
 * <p>2587 裁定：flush finally 不再清空新容器——flush 窗口内再入 enqueue 的延迟任务**保留在
 * 字段中、移交下一轮 flush 执行**（原「finally 无条件清空新容器」静默丢弃面已修）。原 2553-G
 * 的「跨 flush 重放已消费任务」防线由 swap-then-capture 承载：已消费任务在被捕获的旧快照里，
 * 与字段容器分离，不会被下一轮重放。</p>
 *
 * <p>本钉新契约：①flush 后字段残留的只能是「flush 窗口内再入 enqueue 的未执行任务」；
 * ②下一轮 flush 必须执行移交下来的任务且不重放已消费任务；③无再入的 flush 后字段必须为空
 * （swap 捕获清空）。</p>
 */
public class TestBatchDelayTaskCleared extends AbstractOrmTestCase {

    @Test
    public void pin_reentrantDelayTaskCarriedToNextFlushNotDropped() throws Exception {
        // throws Exception：reflection Field 声明了 NoSuchFieldException/IllegalAccessException（测试侧反射钉）
        orm().runInSession(session -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege college = dao.loadEntityById("1");
            college.setCollegeName("delay-task-carry-pinned");

            AtomicBoolean firstTaskRun = new AtomicBoolean(false);
            BatchActionQueueImpl queue = (BatchActionQueueImpl) ((IOrmSessionImplementor) session)
                    .getBatchActionQueue(null);

            // 第一个延迟任务在 flush 成功收尾时执行；执行期间再入 enqueue 第二个任务——
            // 2587 裁定：该任务落入字段新容器，移交下一轮（缺陷态被 finally 清空静默丢弃）
            queue.addDelayTask("pin-first", () -> {
                firstTaskRun.set(true);
                queue.addDelayTask("pin-carried", () -> {
                });
            });

            session.flush();

            assertTrue(firstTaskRun.get(), "flush 成功后延迟任务必须被执行");

            // 新契约：flush 窗口内再入的任务必须保留（移交下一轮），不得被 finally 清空丢弃
            Field field;
            try {
                field = BatchActionQueueImpl.class.getDeclaredField("delayTasks");
                field.setAccessible(true);
                assertNotNull(field.get(queue),
                        "plan 2587：flush 窗口内再入 enqueue 的延迟任务必须移交下一轮（缺陷态被 finally 清空 -> 红）");
            } catch (ReflectiveOperationException e) {
                throw NopException.adapt(e);
            }
            return null;
        });

        // 第二轮（同 session 语义独立布景）：移交下来的任务必须被执行，且已消费任务不重放
        orm().runInSession(session -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege college = dao.loadEntityById("1");

            AtomicBoolean carriedRun = new AtomicBoolean(false);
            BatchActionQueueImpl queue = (BatchActionQueueImpl) ((IOrmSessionImplementor) session)
                    .getBatchActionQueue(null);
            // 再入腿布景：本轮 flush 窗口内再入一个任务
            college.setCollegeName("delay-task-carry-execute-1");
            queue.addDelayTask("pin-first2", () -> {
                queue.addDelayTask("pin-carried2", () -> carriedRun.set(true));
            });
            session.flush();
            assertTrue(carriedRun.get() == false, "移交任务不得在本轮执行");

            // 下一轮 flush（重新制造 dirty 以通过早退门）：执行移交任务，不重放已消费任务
            college.setCollegeName("delay-task-carry-execute-2");
            session.flush();
            assertTrue(carriedRun.get(), "下一轮 flush 必须执行移交下来的延迟任务（缺陷态被清空丢弃 -> 红）");

            // 已消费任务不得跨轮重放：swap-then-capture 下字段已随消费清空（2553-G 防线）
            try {
                Field field = BatchActionQueueImpl.class.getDeclaredField("delayTasks");
                field.setAccessible(true);
                assertNull(field.get(queue), "无再入 flush 后 delayTasks 字段必须为空（swap 捕获清空，2553-G 防线）");
            } catch (ReflectiveOperationException e) {
                throw NopException.adapt(e);
            }
            return null;
        });
    }
}
