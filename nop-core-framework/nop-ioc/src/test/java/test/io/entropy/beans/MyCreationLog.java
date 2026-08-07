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
 * 记录bean创建顺序，用于断言异步启动时前置约束仍然成立。
 */
public class MyCreationLog {
    public static final List<String> order = new ArrayList<>();

    public static void record(String beanId) {
        synchronized (order) {
            order.add(beanId);
        }
    }

    public static void reset() {
        synchronized (order) {
            order.clear();
        }
    }
}