/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
