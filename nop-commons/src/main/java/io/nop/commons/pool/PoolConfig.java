/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.pool;

public class PoolConfig {
    private int maxActive;
    private int maxIdle;
    private int minIdle;

    /**
     * 最多允许多少创建请求线程进入等待队列
     */
    private int maxWaitCount;

    /**
     * 处于idle状态时间过长的对象将被自动关闭
     */
    private int idleTimeout;

    /**
     * 空闲超过一定时间后，会从idle队列中移出，直到空闲对象个数小于等于minIdle
     */
    private int softIdleTimeout;

    /**
     * 当没有空闲对象时，等待多少时间
     */
    private int waitTimeout;

    /**
     * 自动关闭超时使用的对象
     */
    private int removeAbandonedTimeout;

    private boolean testOnBorrow;
    private boolean testOnReturn;
    private boolean testWhileIdle;

    private boolean queueIdleToTail = true;

    private long timeBetweenEvictionRunsMillis;

    /**
     * 自动创建minIdle个对象
     */
    private boolean autoInitMinIdle;

    public boolean isQueueIdleToTail() {
        return queueIdleToTail;
    }

    public void setQueueIdleToTail(boolean queueIdleToTail) {
        this.queueIdleToTail = queueIdleToTail;
    }

    public int getMaxWaitCount() {
        return maxWaitCount;
    }

    public void setMaxWaitCount(int maxWaitCount) {
        this.maxWaitCount = maxWaitCount;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }

    public int getMaxIdle() {
        return maxIdle;
    }

    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    /**
     * 如果非0，则当前没有空闲对象时，可以等待一段时间
     *
     * @return 毫秒为单位
     */
    public int getWaitTimeout() {
        return waitTimeout;
    }

    public void setWaitTimeout(int waitTimeout) {
        this.waitTimeout = waitTimeout;
    }

    /**
     * 如果长时间不返回缓冲池，则主动回收
     *
     * @return
     */
    public int getRemoveAbandonedTimeout() {
        return removeAbandonedTimeout;
    }

    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        this.removeAbandonedTimeout = removeAbandonedTimeout;
    }

    public int getSoftIdleTimeout() {
        return softIdleTimeout;
    }

    public void setSoftIdleTimeout(int softIdleTimeout) {
        this.softIdleTimeout = softIdleTimeout;
    }

    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    public void setTestOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    public boolean isTestOnReturn() {
        return testOnReturn;
    }

    public void setTestOnReturn(boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }

    public boolean isTestWhileIdle() {
        return testWhileIdle;
    }

    public void setTestWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    public long getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    public void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

    public boolean isAutoInitMinIdle() {
        return autoInitMinIdle;
    }

    public void setAutoInitMinIdle(boolean autoInitMinIdle) {
        this.autoInitMinIdle = autoInitMinIdle;
    }
}