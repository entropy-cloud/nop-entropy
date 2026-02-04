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
 * 测试引用force-lazy-property的Bean
 */
public class MyReferForceLazyBean {
    private List<String> executionOrder = new ArrayList<>();
    private MyForceLazyBean forceLazyRef;
    private MyNormalRefBean normalRef;
    private boolean inited = false;

    public MyReferForceLazyBean() {
        executionOrder.add("constructor");
    }

    public void init() {
        inited = true;
        executionOrder.add("init-method");
    }

    public MyForceLazyBean getForceLazyRef() {
        return forceLazyRef;
    }

    public void setForceLazyRef(MyForceLazyBean forceLazyRef) {
        executionOrder.add("setForceLazyRef");
        this.forceLazyRef = forceLazyRef;
    }

    public MyNormalRefBean getNormalRef() {
        return normalRef;
    }

    public void setNormalRef(MyNormalRefBean normalRef) {
        executionOrder.add("setNormalRef");
        this.normalRef = normalRef;
    }

    public boolean isInited() {
        return inited;
    }

    public List<String> getExecutionOrder() {
        return executionOrder;
    }
}
