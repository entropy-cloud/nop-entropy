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
}
