/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

/**
 * C4-003 夹具：正常创建并登记销毁（destroy-method），用于断言失败终态收敛时已建 bean 被销毁。
 */
public class TestStartOkBean {
    public TestStartOkBean() {
        MyCreationLog.record("create:okBean");
    }

    public void destroyMe() {
        MyCreationLog.record("destroy:okBean");
    }
}
