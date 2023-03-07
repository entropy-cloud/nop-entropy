/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.xlang.ast._gen._ArrowFunctionExpression;
import io.nop.xlang.scope.LexicalScope;

public class ArrowFunctionExpression extends _ArrowFunctionExpression implements FunctionExpression {
    private LexicalScope lexicalScope;
    private String funcName;

    public void setFuncName(String funcName) {
        this.funcName = funcName;
    }

    public String getFuncName() {
        return funcName;
    }

    @Override
    public LexicalScope getLexicalScope() {
        return lexicalScope;
    }

    public void setLexicalScope(LexicalScope lexicalScope) {
        Guard.checkArgument(lexicalScope != null, "functionScope");
        this.lexicalScope = lexicalScope;
    }

    public int getSlotCount() {
        return lexicalScope.getSlotCount();
    }

    public boolean isExpression() {
        return !(getBody() instanceof Statement);
    }

    @Override
    public int getDemandArgCount() {
        if (getParams().isEmpty())
            return 0;

        int i;
        for (i = getParams().size() - 1; i >= 0; i--) {
            ParameterDeclaration param = getParams().get(i);
            if (param.getInitializer() == null) {
                break;
            }
        }
        return getParams().size() - i;
    }

    @Override
    public int getClosureVarCount() {
        return this.getLexicalScope().getClosureCount();
    }

}