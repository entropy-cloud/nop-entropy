package io.nop.datav.service;

import io.nop.dao.txn.ITransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 裁定⑫（plan 2526，CL-019）：调度注册/注销副作用 afterCommit 后置。
 *
 * <p>进程内调度器状态（cron job）不随 DB 事务回滚——事务体内先 unregister/register、
 * 后续步骤失败回滚时会出现「DB 行 ENABLED 而 job 已消失」的静默停摆窗口。本工具把
 * register/unregister 调用从事务体内移至 {@code transactionTemplate.afterCommit} 回调：
 * 回滚即不触发（job 保留，与回滚后的 DB 行一致）；提交后才执行（与提交后的 DB 行一致）。
 * 形态沿模块内 AR-2 先例 {@code submitExecutionAfterCommit}（含 {@code isTransactionOpened}
 * 守护——无事务上下文直接执行，语义与调用点原状等价）。</p>
 *
 * <p><b>回调内失败契约</b>：afterCommit 回调内 register/unregister 失败 catch + 结构化 WARN
 * （commit 已完成、无回滚可用，失败按日志可见；自愈语义归裁定⑬同款延迟重试面）。
 * 无事务直行路径保持原传播语义（异常上抛，调用点原状）。</p>
 */
public final class NopDatavSchedulerAfterCommit {

    private static final Logger LOG = LoggerFactory.getLogger(NopDatavSchedulerAfterCommit.class);

    private NopDatavSchedulerAfterCommit() {
    }

    /**
     * 在事务提交后执行调度注册/注销副作用；无事务上下文时立即执行。
     *
     * @param transactionTemplate 事务模板（{@code null} 视为无事务上下文，直接执行）
     * @param site                结构化日志站点标识（回调内失败 WARN 可 grep）
     * @param action              register/unregister 副作用
     */
    public static void run(ITransactionTemplate transactionTemplate, String site, Runnable action) {
        if (transactionTemplate != null && transactionTemplate.isTransactionOpened(null)) {
            transactionTemplate.afterCommit(null, () -> {
                try {
                    action.run();
                } catch (Exception e) {
                    LOG.warn("nop.datav.scheduler-side-effect-failed-after-commit: site={}", site, e);
                }
            });
        } else {
            action.run();
        }
    }
}
