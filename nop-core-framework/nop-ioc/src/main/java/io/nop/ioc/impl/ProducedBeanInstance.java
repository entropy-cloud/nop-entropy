/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import java.lang.reflect.InvocationHandler;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 如果存在beanMethod，则createdBean为factoryBean, bean为对factoryBean.beanMethod()的返回结果进行aopProxy处理后的最终对象
 * 如果不存在beanMethod，则createdBean则为根据bean的class设置所创建的InvocationHandler对象，而bean为经过aopProxy处理的结果
 */
public class ProducedBeanInstance {
    private final Object createdBean;
    private final Function<ProducedBeanInstance, Object> initFunc;
    private final Consumer<ProducedBeanInstance> lazyPropySetFunc;
    private final Consumer<ProducedBeanInstance> delayActionFunc;

    public static final int STATUS_CREATED = 0;
    public static final int STATUS_INITIALIZED = 1;
    public static final int STATUS_LAZY_PROPERTY_SET = 2;
    public static final int STATUS_DELAY_ACTION_RUN = 3;

    private Object bean;
    private int status = STATUS_CREATED;

    public ProducedBeanInstance(Object createdBean, Function<ProducedBeanInstance, Object> initFunc,
                                Consumer<ProducedBeanInstance> lazyPropySetFunc,
                                Consumer<ProducedBeanInstance> delayActionFunc) {
        this.createdBean = createdBean;
        this.initFunc = initFunc;
        this.lazyPropySetFunc = lazyPropySetFunc;
        this.delayActionFunc = delayActionFunc;
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

    public synchronized void runUntil(int initLevel) {
        if (initLevel == STATUS_CREATED)
            return;

        while (status < initLevel) {
            if (status < STATUS_INITIALIZED) {
                bean = initFunc.apply(this);
                status = STATUS_INITIALIZED;
            } else if (status < STATUS_LAZY_PROPERTY_SET) {
                if (lazyPropySetFunc != null) {
                    lazyPropySetFunc.accept(this);
                }
                status = STATUS_LAZY_PROPERTY_SET;
            } else if (status < STATUS_DELAY_ACTION_RUN) {
                if (delayActionFunc != null) {
                    delayActionFunc.accept(this);
                }
                status = STATUS_DELAY_ACTION_RUN;
            } else {
                break;
            }
        }
    }

    public synchronized void setBean(Object bean) {
        this.bean = bean;
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

    public synchronized void setHandler(InvocationHandler handler) {
        ((DelegateInvocationHandler) bean).setHandler(handler);
    }
}