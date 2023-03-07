/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.xlang.ast._gen._FunctionDeclaration;
import io.nop.xlang.scope.LexicalScope;

public class FunctionDeclaration extends _FunctionDeclaration implements FunctionExpression {

    @Override
    public LexicalScope getLexicalScope() {
        return getName().getVarDeclaration().getFunctionScope();
    }

    public void setLexicalScope(LexicalScope lexicalScope) {
        Guard.checkArgument(lexicalScope != null, "functionScope");
        getName().getVarDeclaration().setFunctionScope(lexicalScope);
    }

    public int getSlotCount() {
        return getLexicalScope().getSlotCount();
    }

    public int getClosureVarCount() {
        return getLexicalScope().getClosureCount();
    }

    public int getDemandArgCount() {
        if (getParams().isEmpty())
            return 0;

        int i;
        for (i = getParams().size() - 1; i >= 0; i--) {
            ParameterDeclaration param = getParams().get(i);
            if (param.getInitializer() == null) {
                return i + 1;
            }
        }
        return 0;
    }

    public String getFuncName() {
        return getName().getName();
    }
}