/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

public class MyDestroyBean {

    private boolean destroyed;

    public MyDestroyBean() {
        System.out.println("MyDestroyBean：constructor.....");
    }

    public void destroy() {
        destroyed = true;
        System.out.println("MyDestroyBean：destroy.....");
    }

    // ----------------------

    public boolean isDestroyed() {
        return destroyed;
    }

    public void setDestroyed(boolean destroyed) {
        this.destroyed = destroyed;
    }

}
