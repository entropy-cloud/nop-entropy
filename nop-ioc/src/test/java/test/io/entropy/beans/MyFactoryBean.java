/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package test.io.entropy.beans;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

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
