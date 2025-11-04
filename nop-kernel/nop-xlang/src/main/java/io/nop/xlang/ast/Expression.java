/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.core.type.IGenericType;
import io.nop.xlang.ast._gen._Expression;
import io.nop.xlang.ast.print.XLangSourcePrinter;
import io.nop.xlang.ast.trans.XLangASTTransformer;

import java.util.Objects;

public abstract class Expression extends _Expression {
    private transient IGenericType returnTypeInfo;

    @JsonIgnore
    public IGenericType getReturnTypeInfo() {
        return returnTypeInfo;
    }

    public void setReturnTypeInfo(IGenericType returnTypeInfo) {
        this.returnTypeInfo = returnTypeInfo;
    }

    public String toString() {
        try {
            return toExprString();
        } catch (Exception e) {
            return getClass().getSimpleName() + "@" + Objects.hashCode(this) + ":" + e;
        }
    }

    public String toExprString() {
        XLangSourcePrinter out = new XLangSourcePrinter();
        out.visit(this);
        return out.getResult();
    }

    public Expression replaceIdentifier(String name, Object value) {
        return XLangASTTransformer.replaceIdentifier(this, name, value);
    }
}