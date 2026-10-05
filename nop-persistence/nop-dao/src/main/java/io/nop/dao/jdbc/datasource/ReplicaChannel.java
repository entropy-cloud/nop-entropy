/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import javax.sql.DataSource;

/**
 * 读写分离（plan 2319）从库通道：物理从库引用 + 进程内故障状态机。
 *
 * <p>状态迁移（设计文档 §3.5）：正常 --连续失败达threshold--> 冷却 --冷却期满--> 半开（放行探测）--探测成功--> 正常；
 * 半开探测失败则重新进入冷却。状态为进程内存量，无外部依赖。</p>
 */
public class ReplicaChannel {
    private final String name;
    private final DataSource dataSource;

    private int consecutiveFailures;
    private volatile long cooldownUntil;
    private volatile String lastErrorMessage;

    public ReplicaChannel(String name, DataSource dataSource) {
        this.name = name;
        this.dataSource = dataSource;
    }

    public String getName() {
        return name;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * 冷却期内不可参与选择
     */
    public boolean isCooling(long now) {
        return now < cooldownUntil;
    }

    /**
     * 曾进入过冷却且冷却已期满：处于半开探测状态
     */
    public boolean isHalfOpen(long now) {
        return cooldownUntil > 0 && now >= cooldownUntil;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        cooldownUntil = 0;
        lastErrorMessage = null;
    }

    /**
     * 记录一次获取连接失败。连续失败达到threshold进入冷却；半开探测失败立即重新冷却（不再累积计数）
     */
    public synchronized void recordFailure(int threshold, long cooldownMillis, long now) {
        if (cooldownUntil > 0 && now >= cooldownUntil) {
            // 半开探测失败：重新进入冷却
            cooldownUntil = now + cooldownMillis;
            consecutiveFailures = 0;
            return;
        }
        consecutiveFailures++;
        if (threshold > 0 && consecutiveFailures >= threshold) {
            cooldownUntil = now + cooldownMillis;
            consecutiveFailures = 0;
        }
    }

    public void setLastErrorMessage(String message) {
        this.lastErrorMessage = message;
    }

    /**
     * 人类可读状态摘要，用于全从库不可用时的错误信息
     */
    public String describe(long now) {
        StringBuilder sb = new StringBuilder(name);
        if (isCooling(now)) {
            sb.append(":cooling(retryAfterMs=").append(cooldownUntil - now).append(')');
        } else if (isHalfOpen(now)) {
            sb.append(":half-open");
        } else {
            sb.append(":available");
        }
        if (lastErrorMessage != null) {
            sb.append("(lastError=").append(lastErrorMessage).append(')');
        }
        return sb.toString();
    }
}
