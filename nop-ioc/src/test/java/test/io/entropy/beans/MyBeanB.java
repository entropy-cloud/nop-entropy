/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class MyBeanB {

    private MyBeanA a;
    private MyBeanC c;

    /**
     * 初始化方法
     */
    public void init() {
        System.out.println("MyBeanB：inited.........");
    }

    /**
     * 销毁
     */
    public void destroy() {
        System.out.println("MyBeanB：destroyed.........");
    }

    // --------------------------------------

    public MyBeanA getA() {
        return a;
    }

    public void setA(MyBeanA a) {
        this.a = a;
    }

    public MyBeanC getC() {
        return c;
    }

    public void setC(MyBeanC c) {
        this.c = c;
    }

}
