/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试lazy-property与delay-method执行顺序的Bean
 */
public class MyLazyPropertyWithDelayBean {
    private List<String> executionOrder = new ArrayList<>();
    private String lazyProp;
    private String normalProp;
    private boolean inited = false;
    private boolean delayed = false;

    public MyLazyPropertyWithDelayBean() {
        executionOrder.add("constructor");
    }

    public void init() {
        inited = true;
        executionOrder.add("init-method");
    }

    public void delay() {
        delayed = true;
        executionOrder.add("delay-method");
    }

    public String getLazyProp() {
        return lazyProp;
    }

    public void setLazyProp(String lazyProp) {
        executionOrder.add("setLazyProp");
        this.lazyProp = lazyProp;
    }

    public String getNormalProp() {
        return normalProp;
    }

    public void setNormalProp(String normalProp) {
        executionOrder.add("setNormalProp");
        this.normalProp = normalProp;
    }

    public boolean isInited() {
        return inited;
    }

    public boolean isDelayed() {
        return delayed;
    }

    public List<String> getExecutionOrder() {
        return executionOrder;
    }
}
