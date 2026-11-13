/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizErrors;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.commons.concurrent.executor.IPartitionedExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 2.3-C4-001（plan 2551 裁定 A）：xbiz 异步 action 的缺省工作执行器 bean（名
 * {@code defaultWorkExecutor}，见 {@code BizConstants.DEFAULT_WORK_EXECUTOR_NAME}）。
 *
 * <p>此前该名全仓无 bean 定义——未显式声明 executor 的异步 action 走按名解析即确定性失败
 * （catch 收敛为 future 失败）。本类以薄包装形态补齐缺省面：<b>不新建线程池</b>，execute 全部
 * 委托平台 {@code GlobalExecutors.globalWorker()}（复用平台治理面：队列/上界/绝缘均为平台契约）。
 *
 * <p>分区语义：{@link #executeForPartition(int, Runnable)} 诚实不支持——缺省执行器无分区串行档，
 * 抛类型化异常快速失败；bizSequential 动作与缺省执行器的组合由
 * {@code BizActionService} 的启动期类型校验拦截（运行期不可达）。
 */
public class DefaultWorkExecutor implements IThreadPoolExecutor, IPartitionedExecutor {

    public static final String BEAN_NAME = "defaultWorkExecutor";

    private final Executor delegate;

    public DefaultWorkExecutor() {
        this(GlobalExecutors.globalWorker());
    }

    /** 测试注入缝：显式委托目标 */
    public DefaultWorkExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable task) {
        delegate.execute(task);
    }

    /**
     * 缺省执行器无分区串行档：bizSequential 动作必须显式声明支持分区的执行器
     * （启动期校验 fail-fast，见 BizActionService#validateExecutors），运行期不可达。
     */
    @Override
    public void executeForPartition(int partitionIndex, Runnable task) {
        throw new NopException(BizErrors.ERR_BIZ_EXECUTOR_NOT_PARTITIONED)
                .param("actions", BEAN_NAME + " (all bizSequential actions)");
    }

    @Override
    public String getName() {
        return BEAN_NAME;
    }

    @Override
    public ThreadPoolConfig getConfig() {
        // 治理配置由委托目标（globalWorker）承载，本包装透传其视图
        return GlobalExecutors.globalWorker().getConfig();
    }

    @Override
    public ThreadPoolStats stats() {
        return GlobalExecutors.globalWorker().stats();
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        return GlobalExecutors.globalWorker().submit(callable);
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return GlobalExecutors.globalWorker().submit(task, result);
    }

    @Override
    public void destroy() {
        // 委托目标为平台共享池，不由本包装销毁（no-op 有意：生命周期归平台容器）
    }

    @Override
    public void refreshConfig() {
        // 配置刷新由委托目标承载（no-op 有意）
    }
}
