/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.app.SimsCollege;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmSession;
import io.nop.orm.session.IOrmSessionImplementor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 2.3-C2-009（plan 2553 裁定 B）钉：swap-then-capture——flush 窗口重入追加不丢失。
 * 变异红：恢复 capture-then-null 后重入追加落被丢弃容器 → pin 红。
 *
 * <p>路径：真实 session + H2（非 mock）。布景：常规 save 动作入队后，注册一个延迟任务
 * （flush 成功后于 flushAsync 的 retFuture.whenComplete 内同步执行，见
 * TestFlushFailure#testFlushFailureSkipsDelayTasks 的成功相位）。延迟任务内
 * {@code flushSave} 重入追加第二条 save action（此刻三捕获槽已被 swap 为新空容器），
 * 随后嵌套 {@code queue.flush()} 重入 flush——新容器必须可被捕获并持久化，
 * 追加动作不得随被丢弃旧容器静默丢失。</p>
 */
public class TestBatchActionQueueSwapCapture extends AbstractOrmTestCase {

    @Test
    public void testReentrantEnqueueDuringFlushWindowSurvives() {
        orm().runInSession((IOrmSession session) -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);

            // 常规动作：随本轮 flush 一并提交
            SimsCollege first = dao.newEntity();
            first.setCollegeId("201");
            first.setCollegeName("College201");
            first.setIntro("intro201");
            first.setShortName("N201");
            dao.saveEntity(first);

            IBatchActionQueue queue = ((IOrmSessionImplementor) session).getBatchActionQueue(null);

            queue.addDelayTask("c2-009-reentrant", () -> {
                // flush 窗口内重入追加：persister.save -> queueSave -> enqueueSave
                // 落入 swap 后的新容器（缺陷态落入已被捕获的旧容器，最终被丢弃）
                SimsCollege second = dao.newEntity();
                second.setCollegeId("202");
                second.setCollegeName("College202");
                second.setIntro("intro202");
                second.setShortName("N202");
                ((IOrmSessionImplementor) session).flushSave(second);

                // 重入 flush：捕获新容器并提交，追加动作不得丢失
                queue.flush();
            });

            session.flush();
            return null;
        });

        // flush 完成后两个动作都必须已持久化
        orm().runInSession((IOrmSession session) -> {
            assertNotNull(daoProvider().daoFor(SimsCollege.class).getEntityById("201"),
                    "常规动作必须持久化");
            assertNotNull(daoProvider().daoFor(SimsCollege.class).getEntityById("202"),
                    "flush 窗口内重入追加的动作必须持久化（缺陷态随被丢弃容器静默丢失 -> 红）");
            return null;
        });
    }

    /**
     * plan 2587 钉：flush 窗口内重入 enqueue「移交下一轮」——finally 不清空新容器。
     * delay task 在 flush 窗口内追加动作但不重入 flush；下一轮 flush 必须执行该动作。
     * 缺陷态（2587 前）：finally 无条件清空新容器 → 追加动作静默丢失 → 本钉红。
     */
    @Test
    public void testReentrantEnqueueCarriedToNextFlushRound() {
        orm().runInSession((IOrmSession session) -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);

            SimsCollege first = dao.newEntity();
            first.setCollegeId("211");
            first.setCollegeName("College211");
            first.setIntro("intro211");
            first.setShortName("N211");
            dao.saveEntity(first);

            IBatchActionQueue queue = ((IOrmSessionImplementor) session).getBatchActionQueue(null);

            queue.addDelayTask("2587-carry-next-round", () -> {
                // flush 窗口内重入追加（落入 swap 后的新容器），但【不】重入 flush——
                // 缺陷态下 finally 清空新容器 → 静默丢失；修复后移交下一轮
                SimsCollege second = dao.newEntity();
                second.setCollegeId("212");
                second.setCollegeName("College212");
                second.setIntro("intro212");
                second.setShortName("N212");
                ((IOrmSessionImplementor) session).flushSave(second);
            });

            // 第一轮 flush：提交 201，delay task 同步执行并追加 202 到新容器
            session.flush();
            // 下一轮 flush：必须执行移交下来的 202（缺陷态下被 finally 清空 → 无任何回调）
            queue.flush();
            return null;
        });

        orm().runInSession((IOrmSession session) -> {
            assertNotNull(daoProvider().daoFor(SimsCollege.class).getEntityById("211"),
                    "常规动作必须持久化");
            assertNotNull(daoProvider().daoFor(SimsCollege.class).getEntityById("212"),
                    "flush 窗口内追加的动作必须由下一轮 flush 执行（缺陷态随 finally 清空静默丢失 -> 红）");
            return null;
        });
    }
}
