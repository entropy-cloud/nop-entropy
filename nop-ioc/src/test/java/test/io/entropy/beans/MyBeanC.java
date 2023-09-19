/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package test.io.entropy.beans;

import io.nop.api.core.annotations.data.DataBean;

import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;

@DataBean
public class MyBeanC {
    private MyBeanA a;

    String path;

    @Resource
    MyBeanA a2;

    @Nullable
    @Resource
    UnknownBean unknownBean;

    @Resource(name = "a2")
    MyBeanA a3;

    static class UnknownBean {

    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public MyBeanA getA3() {
        return a3;
    }

    public MyBeanA getA2() {
        return a2;
    }

    /**
     * 初始化方法
     */
    public void init() {
        System.out.println("MyBeanC：inited.........");
    }

    /**
     * 销毁
     */
    public void destroy() {
        System.out.println("MyBeanC：destroyed.........");
    }

    public MyBeanA getA() {
        return a;
    }

    public void setA(MyBeanA a) {
        this.a = a;
    }
}
