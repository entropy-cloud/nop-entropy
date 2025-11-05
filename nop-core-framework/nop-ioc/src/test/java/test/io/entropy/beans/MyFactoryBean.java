/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class MyFactoryBean {
    boolean inited;
    Object singleton;

    @PostConstruct
    public void afterPropertiesSet() {
        inited = true;
    }

    public boolean isInited() {
        return inited;
    }

    public synchronized Object getObject() throws Exception {
        if (singleton != null)
            return singleton;
        singleton = new MyBeanA();
        return singleton;
    }

    public Class<?> getObjectType() {
        return MyBeanA.class;
    }

    public boolean isSingleton() {
        return true;
    }

    @PreDestroy
    public void destroy() {
        inited = false;
    }
}
