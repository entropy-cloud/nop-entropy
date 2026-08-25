/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.exceptions.NopException;

import java.lang.reflect.InvocationHandler;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.nop.ioc.IocErrors.ARG_BEAN_NAME;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_INIT_SELF_WAIT;

/**
 * 如果存在beanMethod，则createdBean为factoryBean, bean为对factoryBean.beanMethod()的返回结果进行aopProxy处理后的最终对象
 * 如果不存在beanMethod，则createdBean则为根据bean的class设置所创建的InvocationHandler对象，而bean为经过aopProxy处理的结果
 *
 * <p>并发初始化同一 bean 时的加锁纪律：任何容器锁在持有时不得回调用户代码。本类持自身 monitor 时只做短临界区
 * （状态检查/登记/推进），init/lazy/delay 回调一律在锁外由 owner 线程执行。</p>
 */
public class ProducedBeanInstance {
    private final String beanId;
    private final Object createdBean;
    private final Function<ProducedBeanInstance, Object> initFunc;
    private final Consumer<ProducedBeanInstance> lazyPropySetFunc;
    private final Consumer<ProducedBeanInstance> delayActionFunc;

    public static final int STATUS_CREATED = 0;
    public static final int STATUS_PROPERTY_SET = 1;
    public static final int STATUS_INITIALIZED = 2;
    public static final int STATUS_LAZY_PROPERTY_SET = 3;
    public static final int STATUS_DELAY_ACTION_RUN = 4;

    private final Thread createThread;

    private volatile Object bean;
    private int status = STATUS_CREATED;

    /**
     * ioc:proxy + ioc:bean-method 组合下 {@link #bean} 保存的是 JDK 动态代理对象而非
     * {@link DelegateInvocationHandler} 本身，必须额外保存 handler 引用供
     * {@link #setHandler(InvocationHandler)} 回填，不能对代理对象做强转（必抛 CCE）。
     */
    private DelegateInvocationHandler proxyHandler;

    /**
     * 当前推进阶段的 owner 线程。null 表示当前无推进在进行。
     */
    private Thread initThread;

    /**
     * 阶段回调抛出的异常。非 null 时所有等待者被唤醒后重新抛出。
     */
    private Throwable initError;

    public ProducedBeanInstance(String beanId, Object createdBean, Function<ProducedBeanInstance, Object> initFunc,
                                Consumer<ProducedBeanInstance> lazyPropySetFunc,
                                Consumer<ProducedBeanInstance> delayActionFunc) {
        this.beanId = beanId;
        this.createdBean = createdBean;
        this.initFunc = initFunc;
        this.lazyPropySetFunc = lazyPropySetFunc;
        this.delayActionFunc = delayActionFunc;
        this.createThread = Thread.currentThread();
    }

    public Object getCreatedBean() {
        return createdBean;
    }

    public synchronized boolean isInitialized() {
        return status >= STATUS_INITIALIZED;
    }

    public synchronized Object getBean() {
        return bean;
    }

    public synchronized void setBean(Object bean) {
        this.bean = bean;
    }

    /**
     * 属性赋值阶段完成，由创建线程在创建路径内（newObject/setupInstance）调用，不在 runUntil 内执行。
     */
    public void markPropSet() {
        synchronized (this) {
            if (status < STATUS_PROPERTY_SET) {
                status = STATUS_PROPERTY_SET;
                notifyAll();
            }
        }
    }

    /**
     * 属性赋值阶段失败，由创建线程在创建路径内记录异常并唤醒等待者，防止等待者永久挂起。
     */
    public void markPropSetFailed(Throwable e) {
        synchronized (this) {
            if (initError == null)
                initError = e;
            notifyAll();
        }
    }

    /**
     * 推进 bean 生命周期到指定阶段。owner 线程在锁外执行阶段回调，其他线程在条件变量上等待。
     */
    public void runUntil(int initLevel) {
        if (initLevel == STATUS_CREATED)
            return;

        while (true) {
            int phase = -1;
            synchronized (this) {
                if (status >= initLevel) {
                    return;
                }

                if (initError != null)
                    throw NopException.adapt(initError);

                if (status < STATUS_PROPERTY_SET) {
                    // PROPERTY_SET 阶段由创建线程在创建路径内推进，runUntil 绝不执行它。
                    if (createThread == Thread.currentThread())
                        throw selfWait();
                    waitQuietly();
                    continue;
                }

                if (initThread != null) {
                    if (initThread == Thread.currentThread())
                        throw selfWait();
                    waitQuietly();
                    continue;
                }

                // 成为当前阶段推进的 owner
                initThread = Thread.currentThread();
                phase = status;
            }

            // 锁外执行阶段回调
            try {
                runPhaseCallback(phase);
            } catch (RuntimeException | Error e) {
                synchronized (this) {
                    if (initError == null)
                        initError = e;
                    initThread = null;
                    notifyAll();
                }
                throw e;
            }

            // 短临界区推进状态并唤醒等待者
            synchronized (this) {
                if (initError == null) {
                    status = phase + 1;
                }
                initThread = null;
                notifyAll();
            }
        }
    }

    private void runPhaseCallback(int phase) {
        switch (phase) {
            case STATUS_PROPERTY_SET:
                bean = initFunc.apply(this);
                break;
            case STATUS_INITIALIZED:
                if (lazyPropySetFunc != null)
                    lazyPropySetFunc.accept(this);
                break;
            case STATUS_LAZY_PROPERTY_SET:
                if (delayActionFunc != null)
                    delayActionFunc.accept(this);
                break;
            default:
                break;
        }
    }

    private NopException selfWait() {
        return new NopException(ERR_IOC_BEAN_INIT_SELF_WAIT).param(ARG_BEAN_NAME, beanId);
    }

    private void waitQuietly() {
        try {
            wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        }
    }

    public void checkBeanInitialized() {
        runUntil(STATUS_INITIALIZED);
    }

    public void checkBeanLazyPropSet() {
        runUntil(STATUS_LAZY_PROPERTY_SET);
    }

    public void checkBeanDelayActionRun() {
        runUntil(STATUS_DELAY_ACTION_RUN);
    }

    /**
     * bean 为围绕 {@link DelegateInvocationHandler} 创建的 JDK 动态代理时，代理对象与 handler
     * 必须同时登记（见 {@link #proxyHandler}），随后 init 阶段经 {@link #setHandler} 回填真实 handler。
     */
    public synchronized void setBeanWithProxyHandler(Object bean, DelegateInvocationHandler handler) {
        setBean(bean);
        this.proxyHandler = handler;
    }

    public synchronized void setHandler(InvocationHandler handler) {
        DelegateInvocationHandler delegate = this.proxyHandler;
        if (delegate == null)
            delegate = (DelegateInvocationHandler) bean;
        delegate.setHandler(handler);
    }
}