/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.annotations.config.ConfigBean;

@ConfigBean
public class ThreadPoolConfig extends BaseThreadPoolConfig {
    private static final long serialVersionUID = -23954842588599473L;

    private String name;
    private int corePoolSize = 4;

    private int maxPoolSize = 20;

    private int keepAliveSeconds = 60;

    // C4-011（plan 2548）：threadPriority / allowCoreThreadTimeOut 已删除——全仓零 Java 消费者、
    // 零配置键使用者（yaml/xml/properties 均无），删除死旋钮；契约变更已在 owner doc 声明

    private int queueCapacity = 10000;

    private boolean threadDaemon = false;

    private boolean enableMetrics = true;

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public boolean isThreadDaemon() {
        return threadDaemon;
    }

    public void setThreadDaemon(boolean threadDaemon) {
        this.threadDaemon = threadDaemon;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnableMetrics() {
        return enableMetrics;
    }

    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }
}