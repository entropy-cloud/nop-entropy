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
 * 测试ioc:lazy-property属性的Bean
 */
public class MyLazyPropertyBean {
    private List<String> executionOrder = new ArrayList<>();
    private String lazyPropertyProp;
    private String normalProp;
    private boolean inited = false;

    public MyLazyPropertyBean() {
        executionOrder.add("constructor");
    }

    public void init() {
        inited = true;
        executionOrder.add("init-method");
    }

    public String getLazyPropertyProp() {
        return lazyPropertyProp;
    }

    public void setLazyPropertyProp(String lazyPropertyProp) {
        executionOrder.add("setLazyPropertyProp");
        this.lazyPropertyProp = lazyPropertyProp;
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

    public List<String> getExecutionOrder() {
        return executionOrder;
    }
}
