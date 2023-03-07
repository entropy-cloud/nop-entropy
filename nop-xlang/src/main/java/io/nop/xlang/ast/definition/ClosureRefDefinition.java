/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast.definition;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.Symbol;
import io.nop.core.type.IGenericType;
import io.nop.xlang.ast.IdentifierKind;
import io.nop.xlang.ast.XLangIdentifierDefinition;

public class ClosureRefDefinition implements XLangIdentifierDefinition {
    private SourceLocation loc;
    private LocalVarDeclaration refDefinition;

    /**
     * 在当前函数所对应的EvalFrame中的下标
     */
    private int slot = -1;

    public ClosureRefDefinition(SourceLocation loc, LocalVarDeclaration refDefinition) {
        this.loc = loc;
        this.refDefinition = refDefinition;
    }

    public String toString() {
        return "ClosureRef:slot=" + slot + ",ref=" + refDefinition;
    }

    public SourceLocation getLocation() {
        return loc;
    }

    @Override
    public IGenericType getResolvedType() {
        return refDefinition.getResolvedType();
    }

    public boolean isUseRef() {
        return refDefinition.isUseRef();
    }

    @Override
    public boolean isAllowAssignment() {
        return refDefinition.isAllowAssignment();
    }

    public String getIdentifierName() {
        return getVarDeclaration().getIdentifierName();
    }

    public boolean isRefToFunc() {
        return getVarDeclaration().getIdentifierKind() == IdentifierKind.FUNC_DECL;
    }

    public Symbol getToken() {
        return getVarDeclaration().getToken();
    }

    /**
     * 向上查找到真正的变量声明
     */
    public LocalVarDeclaration getVarDeclaration() {
        return refDefinition;
    }

    public boolean isChanged() {
        return getVarDeclaration().isChanged();
    }

    public int getVarSlot() {
        return slot;
    }

    public void setVarSlot(int slot) {
        this.slot = slot;
    }
}