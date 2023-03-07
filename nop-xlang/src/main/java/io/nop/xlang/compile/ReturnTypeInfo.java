/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.compile;

import io.nop.core.type.IGenericType;
import io.nop.xlang.ast.Expression;

public class ReturnTypeInfo {
    public static final ReturnTypeInfo CONTINUE = new ReturnTypeInfo();

    private IGenericType returnType;
    private Expression returnAST;

    private boolean otherBranchNoReturn;

    public IGenericType getReturnType() {
        return returnType;
    }

    public void setReturnType(IGenericType returnType) {
        this.returnType = returnType;
    }

    public Expression getReturnAST() {
        return returnAST;
    }

    public void setReturnAST(Expression returnAST) {
        this.returnAST = returnAST;
    }

    public boolean isOtherBranchNoReturn() {
        return otherBranchNoReturn;
    }

    public void setOtherBranchNoReturn(boolean otherBranchNoReturn) {
        this.otherBranchNoReturn = otherBranchNoReturn;
    }
}
