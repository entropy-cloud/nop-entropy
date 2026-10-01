package io.nop.batch.dao.store;

import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.dao.entity.NopBatchTask;
import io.nop.dao.api.IEntityDao;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 测试专用：在 loadExistingTask 的两条返回路径上设置会合栅栏，
 * 使两实例确定性地在"存在性检查完成"这一点会合后再放行，复现真实的 check-then-act 竞态窗口。
 * 两个栅栏为 null 时完全透传，不影响其他用例。
 */
public class RaceWindowDaoBatchStateStore extends DaoBatchStateStore {

    /** 全新key竞态：loadExistingTask 未命中（null）路径的会合栅栏 */
    public volatile CountDownLatch firstLoadLatch;

    /** 重启竞态：loadExistingTask 命中已有行（非null）路径的会合栅栏 */
    public volatile CountDownLatch existingLoadLatch;

    @Override
    protected NopBatchTask loadExistingTask(IEntityDao<NopBatchTask> dao, IBatchTaskContext context) {
        NopBatchTask task = super.loadExistingTask(dao, context);
        CountDownLatch latch = task == null ? this.firstLoadLatch : this.existingLoadLatch;
        if (latch != null) {
            // 会合点在存在性检查之后：保证两实例都拿到相同的检查结果后，才同时放行后续启动路径
            latch.countDown();
            try {
                if (!latch.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("race window threads did not converge in time");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for race window", e);
            }
        }
        return task;
    }
}
