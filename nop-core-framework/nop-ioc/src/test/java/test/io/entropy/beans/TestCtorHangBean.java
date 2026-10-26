/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

/**
 * C2-001 有界兜底夹具：构造器阻塞（持 BD 锁不放），验证等待方超时类型化错误而非永久挂死。
 */
public class TestCtorHangBean {
    public TestCtorHangBean() {
        TestBeanDefFlags.ctorHangEnterAndWait();
    }
}
