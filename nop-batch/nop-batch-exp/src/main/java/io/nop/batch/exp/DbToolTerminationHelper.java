/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.exp;

import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1.7-C6-004（plan 2545 W-09；closure audit R2 MJ-1）：destroy() 后的有界等待。
 * <p>
 * {@code destroy()}=shutdown 不等待，失败路径 {@code execute()} 返回后兄弟表任务仍在后台运行
 * 会并发续写状态文件、而 CLI 已返回错误码。以 {@code stats()} 做有界等待（活跃线程与队列同时
 * 归零即视为已停），上限缺省 30s，超时 WARN 留痕后放弃——不引入平台设施改动
 * （{@code IThreadPoolExecutor} 无 isTerminated 访问口）。
 * <p>
 * 抽取为独立可见类是为使「有界等待」本身可被单测直接验证（原为两工具各自的 private static，
 * 无测试可触达）。
 */
public final class DbToolTerminationHelper {
    static final Logger LOG = LoggerFactory.getLogger(DbToolTerminationHelper.class);

    public static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    private DbToolTerminationHelper() {
    }

    /**
     * 等待池活跃线程与队列同时归零。
     *
     * @return true 表示观察到已停（或池无 stats / 被中断），false 表示超时后仍有未停任务
     */
    public static boolean waitTerminated(IThreadPoolExecutor executor, String timeoutLogKey, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            ThreadPoolStats stats = executor.stats();
            if (stats == null || (stats.getActiveCount() <= 0 && stats.getQueueSize() <= 0))
                return true;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
        LOG.warn(timeoutLogKey);
        return false;
    }

    public static boolean waitTerminated(IThreadPoolExecutor executor, String timeoutLogKey) {
        return waitTerminated(executor, timeoutLogKey, DEFAULT_TIMEOUT_MILLIS);
    }
}
