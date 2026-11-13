/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

/**
 * C4-003 失败注入夹具：init 阶段抛出 RuntimeException（并发启动失败传导腿）。
 */
public class TestStartFailInitBean {
    public void initMe() {
        throw new IllegalStateException("simulated-init-failure");
    }
}
