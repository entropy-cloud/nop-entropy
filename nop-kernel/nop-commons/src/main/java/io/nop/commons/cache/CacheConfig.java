/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.cache;

import io.nop.api.core.annotations.config.ConfigBean;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@DataBean
@ConfigBean
public class CacheConfig implements Serializable {
    private static final long serialVersionUID = -3724749759543407079L;

    private boolean async;
    private boolean weakKeys;
    private boolean weakValues;

    private long maximumSize;
    private long maximumWeight;

    private Duration expireAfterWrite;

    private Duration expireAfterAccess;

    private Duration refreshAfterWrite;

    private boolean destroyOnRemove;

    private boolean useMetrics = false;

    /**
     * 1.7-C3-010（plan 2547 裁定 F）：异步加载/刷新所用的 Executor。
     * <p>
     * 缺省 {@code null} = 沿用 Caffeine 默认的 {@code ForkJoinPool.commonPool()}（**保持既有行为**）；
     * 设为非 null 时 {@code LocalCache} 会把该 executor 装配到 Caffeine builder。
     * 定位说明：async 缓存加载通常是 I/O 型，若与 CPU 型短任务共用 commonPool 会互相干扰，
     * 生产环境建议显式指定独立池（如 {@code GlobalExecutors.globalWorker()}）。
     * <p>
     * 注意：本次只提供出口、不改缺省——改缺省会改变既有并发形态（globalWorker 已被长任务占用
     * 面投诉过，存在死锁风险）。
     */
    private transient java.util.concurrent.Executor executor;

    public CacheConfig() {
    }

    public CacheConfig(CacheConfig config) {
        this.weakKeys = config.weakKeys;
        this.weakValues = config.weakValues;
        this.maximumSize = config.maximumSize;
        this.maximumWeight = config.maximumWeight;
        this.expireAfterWrite = config.expireAfterWrite;
        this.expireAfterAccess = config.expireAfterAccess;
        this.async = config.async;
        this.useMetrics = config.useMetrics;
        // C5-003（plan 2548 裁定 D）：拷贝构造必须带上生命周期语义字段，否则克隆路径上
        // destroyOnRemove 恒 false（驱逐回调结构性失效）
        this.destroyOnRemove = config.destroyOnRemove;
        this.refreshAfterWrite = config.refreshAfterWrite;
        // executor 为 transient 池引用：克隆把池引用带出通常不是期望行为，显式不拷贝（Deferred 裁定）
    }

    public CacheConfig cloneInstance() {
        return new CacheConfig(this);
    }

    public static CacheConfig newConfig(int maxSize) {
        CacheConfig config = new CacheConfig();
        config.setMaximumSize(maxSize);
        return config;
    }

    public static CacheConfig newConfig(int maxSize, long expireTimeout) {
        return newConfig(maxSize).expireAfterWrite(Duration.of(expireTimeout, ChronoUnit.MILLIS));
    }

    /**
     * 判断配置变化是否导致生成的cache类型发生变化。如果类型变化，则需要重新构建cache，否则修改参数即可
     */
    public boolean isCacheTypeChanged(CacheConfig config) {
        if (async != config.async)
            return true;

        if (weakKeys != config.weakKeys)
            return true;
        if (weakValues != config.weakValues)
            return true;

        // 如果最大缓存条目数都大于0，则即使配置不相等，也不改变缓存类型
        if (maximumSize != config.maximumSize) {
            if (maximumSize < 0 || config.maximumSize < 0)
                return true;
        }

        if (maximumWeight != config.maximumWeight) {
            if (maximumWeight < 0 || config.maximumWeight < 0)
                return true;
        }
        if (!Objects.equals(expireAfterWrite, config.expireAfterWrite)) {
            if (expireAfterWrite == null || config.expireAfterWrite == null)
                return true;
        }
        if (!Objects.equals(expireAfterAccess, config.expireAfterAccess)) {
            if (expireAfterAccess == null || config.expireAfterAccess == null)
                return true;
        }

        if (!Objects.equals(refreshAfterWrite, config.refreshAfterWrite)) {
            if (refreshAfterWrite == null || config.refreshAfterWrite == null)
                return true;
        }

        return false;
    }

    public CacheConfig useMetrics() {
        this.setUseMetrics(true);
        return this;
    }

    public CacheConfig weakKeys() {
        this.setWeakKeys(true);
        return this;
    }

    public CacheConfig weakValues() {
        this.setWeakValues(true);
        return this;
    }

    public CacheConfig async() {
        this.setAsync(true);
        return this;
    }

    public CacheConfig expireAfterWrite(Duration duration) {
        this.setExpireAfterWrite(duration);
        return this;
    }

    public CacheConfig expireAfterAccess(Duration duration) {
        this.setExpireAfterAccess(duration);
        return this;
    }

    public CacheConfig destroyOnRemove() {
        this.setDestroyOnRemove(true);
        return this;
    }

    public java.util.concurrent.Executor getExecutor() {
        return executor;
    }

    public void setExecutor(java.util.concurrent.Executor executor) {
        this.executor = executor;
    }

    public boolean isUseMetrics() {
        return useMetrics;
    }

    public void setUseMetrics(boolean useMetrics) {
        this.useMetrics = useMetrics;
    }

    public boolean isDestroyOnRemove() {
        return destroyOnRemove;
    }

    public void setDestroyOnRemove(boolean destroyOnRemove) {
        this.destroyOnRemove = destroyOnRemove;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        this.maximumSize = maximumSize;
    }

    public boolean isWeakKeys() {
        return weakKeys;
    }

    public void setWeakKeys(boolean weakKeys) {
        this.weakKeys = weakKeys;
    }

    public boolean isWeakValues() {
        return weakValues;
    }

    public void setWeakValues(boolean weakValues) {
        this.weakValues = weakValues;
    }

    public long getMaximumWeight() {
        return maximumWeight;
    }

    public void setMaximumWeight(long maximumWeight) {
        this.maximumWeight = maximumWeight;
    }

    public Duration getRefreshAfterWrite() {
        return refreshAfterWrite;
    }

    public void setRefreshAfterWrite(Duration refreshAfterWrite) {
        this.refreshAfterWrite = refreshAfterWrite;
    }

    public Duration getExpireAfterWrite() {
        return expireAfterWrite;
    }

    public void setExpireAfterWrite(Duration expireAfterWrite) {
        this.expireAfterWrite = expireAfterWrite;
    }

    public Duration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    public void setExpireAfterAccess(Duration expireAfterAccess) {
        this.expireAfterAccess = expireAfterAccess;
    }
}