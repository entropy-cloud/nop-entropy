/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.context;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import io.nop.api.core.annotations.core.NoReflection;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 服务函数执行的上下文，任何时刻只能有一个线程在访问这一对象。
 */
@NotThreadSafe
public interface IContext extends Executor, AutoCloseable {
    boolean isClosed();

    String getTraceId();

    @NoReflection
    void setTraceId(String traceId);

    /**
     * SAAS模式下的租户id
     */
    String getTenantId();

    @NoReflection
    void setTenantId(String tenantId);

    /**
     * 当前登录用户id
     */
    String getUserId();

    @NoReflection
    void setUserId(String userId);

    String getUserName();

    @NoReflection
    void setUserName(String userName);

    /**
     * 记录在日志中user的唯一标识，如果没有设置，则返回userName
     */
    String getUserRefNo();

    void setUserRefNo(String userRefNo);

    /**
     * 多语言环境下的当前语言
     */
    String getLocale();

    void setLocale(String locale);

    /**
     * 多时区环境下的当前时区
     */
    String getTimezone();

    void setTimezone(String timezone);

    /**
     * API请求可能需要在一定时间内结束，如果超时时间设置大于0，则具体调用时可以检查context上的超时时间来判断是否已经超时。
     */
    long getCallExpireTime();

    void setCallExpireTime(long expireTime);

    String getCallIp();

    void setCallIp(String callIp);

    Map<String, Object> getPropagateRpcHeaders();

    void setPropagateRpcHeaders(Map<String, Object> propagateHeaders);

    @JsonAnyGetter
    Map<String, Object> getAttrs();

    Object getAttribute(String name);

    void setAttribute(String name, Object value);

    void removeAttribute(String name);

    boolean removeAttribute(String name, Object value);

    /**
     * 记录内部对应的VertxContext
     */
    Object getInternalContext();

    /**
     * 将任务放入到context的执行队列中等待执行
     *
     * @param task 待执行的任务
     */
    void runOnContext(Runnable task);

    /**
     * 如果当前处于执行线程之上，则直接执行，否则放入到context的执行队列中
     *
     * @param task 待执行的任务
     */
    void execute(Runnable task);

    /**
     * 当前线程是否正在执行这个context的任务队列
     *
     * @return
     */
    boolean isRunningOnContext();

    /**
     * 利用内部workerExecutor来执行
     *
     * @param task    待执行的任务，返回值可能是普通对象或者CompletionStage对象
     * @param ordered 如果是有序执行，则投递到context的队列，否则只是选择一个worker线程，把context设置为上下文对象来执行
     * @return 重新回到当前上下文执行回调。
     */
    <T> CompletionStage<T> executeBlocking(Supplier<?> task, boolean ordered);

    /**
     * 解除与VertxContext的绑定
     */
    void close();

    /**
     * 等待future返回。如果在此过程中有异步回调需要执行，则执行回调。等待过程不会阻塞回调函数的执行
     */
    <T> T syncGet(CompletionStage<T> future);
}