/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package test.io.entropy.beans;

import io.nop.core.lang.eval.IEvalAction;

public class MyXplBean {
    IEvalAction xplA;

    IEvalAction xplB;

    IEvalAction exprC;

    public IEvalAction getXplA() {
        return xplA;
    }

    public void setXplA(IEvalAction xplA) {
        this.xplA = xplA;
    }

    public IEvalAction getXplB() {
        return xplB;
    }

    public void setXplB(IEvalAction xplB) {
        this.xplB = xplB;
    }

    public void setExprC(IEvalAction exprC) {
        this.exprC = exprC;
    }

    public IEvalAction getExprC() {
        return exprC;
    }
}
