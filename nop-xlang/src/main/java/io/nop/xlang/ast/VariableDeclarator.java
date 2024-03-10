/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._VariableDeclarator;

public class VariableDeclarator extends _VariableDeclarator {

    public static VariableDeclarator valueOf(SourceLocation loc, XLangASTNode id, NamedTypeNode type, Expression init) {
        VariableDeclarator node = new VariableDeclarator();
        node.setLocation(loc);
        node.setId((XLangASTNode) id);
        node.setVarType(type);
        node.setInit(init);
        return node;
    }
}