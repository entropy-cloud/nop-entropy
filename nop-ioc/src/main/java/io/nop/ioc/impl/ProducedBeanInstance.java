/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import java.lang.reflect.InvocationHandler;

/**
 * 如果存在beanMethod，则createdBean为factoryBean, bean为对factoryBean.beanMethod()的返回结果进行aopProxy处理后的最终对象
 * 如果不存在beanMethod，则createdBean则为根据bean的class设置所创建的InvocationHandler对象，而bean为经过aopProxy处理的结果
 */
public class ProducedBeanInstance {
    private final Object createdBean;
    private Object bean;

    public ProducedBeanInstance(Object createdBean) {
        this.createdBean = createdBean;
    }

    public void setHandler(InvocationHandler handler) {
        ((DelegateInvocationHandler) bean).setHandler(handler);
    }

    public Object getCreatedBean() {
        return createdBean;
    }

    public synchronized Object getBean() {
        return bean;
    }

    public synchronized void setBean(Object bean) {
        this.bean = bean;
    }
}
