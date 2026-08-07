/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

public class MyDependsBeanY {
    public static int createdCount = 0;

    public MyDependsBeanY() {
        createdCount++;
    }

    public static void reset() {
        createdCount = 0;
    }
}
