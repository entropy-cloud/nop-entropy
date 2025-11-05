/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

public class MyInitBean {

    private boolean inited;

    public MyInitBean() {
        System.out.println("MyDestoryBean：constructor.....");
    }

    public void init() {
        inited = true;
        System.out.println("MyDestoryBean：init.....");
    }

    // ----------------------

    public boolean isInited() {
        return inited;
    }

    public void setInited(boolean inited) {
        this.inited = inited;
    }

}
