/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.core.lang.ast.ASTNode;
import io.nop.xlang.scope.LexicalScope;

public abstract class XLangASTNode extends ASTNode<XLangASTNode> implements IXLangASTNode {
    public abstract XLangASTKind getASTKind();

    public XLangASTKind getASTParentKind() {
        XLangASTNode parent = getASTParent();
        if (parent == null)
            return null;
        return parent.getASTKind();
    }

    public String getASTType() {
        return getASTKind().toString();
    }

    public abstract XLangASTNode deepClone();

    public LexicalScope getLexicalScope() {
        XLangASTNode node = getASTParent();
        if (node == null)
            return null;
        return node.getLexicalScope();
    }
}