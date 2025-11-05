/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

public class MyLazyInitBean2 {
    public static int createdCount = 0;

    public MyLazyInitBean2() {
        createdCount++;
        System.out.println("MyLazyInitBean2：Constructor.....");
    }
}
