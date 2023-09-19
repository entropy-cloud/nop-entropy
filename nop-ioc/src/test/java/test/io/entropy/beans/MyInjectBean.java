/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package test.io.entropy.beans;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

public class MyInjectBean {

    boolean inited;

    @Resource
    MyBeanA a;

    @Resource(name = "b")
    Object b;

    public boolean isInited() {
        return inited;
    }

    @PostConstruct
    public void init() {
        inited = true;
    }

    public MyBeanA getA() {
        return a;
    }

    public Object getB() {
        return b;
    }
}
