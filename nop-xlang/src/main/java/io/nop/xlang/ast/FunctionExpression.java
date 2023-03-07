/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.xlang.scope.LexicalScope;

import java.util.List;

public interface FunctionExpression extends ISourceLocationGetter, IWithLexicalScope {
    String getFuncName();

    List<ParameterDeclaration> getParams();

    LexicalScope getLexicalScope();

    void setLexicalScope(LexicalScope scope);

    default String[] getSlotNames() {
        return getLexicalScope().getSlotNames();
    }

    int getSlotCount();

    int getDemandArgCount();

    int getClosureVarCount();

    default int getArgCount() {
        return getParams().size();
    }

    Expression getBody();
}
