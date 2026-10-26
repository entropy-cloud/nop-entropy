/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

/**
 * C4-008 F5 夹具：delay-method 抛出异常——原实现成功分支回调异常静默、started 永不翻转。
 */
public class TestStartFailDelayBean {
    public void delayMe() {
        throw new IllegalStateException("simulated-delay-failure");
    }
}
