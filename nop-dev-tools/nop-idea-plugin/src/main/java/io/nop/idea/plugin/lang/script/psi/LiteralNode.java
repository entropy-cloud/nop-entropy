/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.jetbrains.annotations.NotNull;

/**
 * 字面量节点
 * <p/>
 * 该节点 AST 树为：
 * <pre>
 * LiteralNode(literal)
 *   RuleSpecNode(literal_numeric)
 *     PsiElement(DecimalIntegerLiteral)('3')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-02
 */
public class LiteralNode extends RuleSpecNode {
    private LeafPsiElement literal;

    public LiteralNode(@NotNull ASTNode node) {
        super(node);
    }

    public LeafPsiElement getLiteral() {
        if (literal == null || !literal.isValid()) {
            literal = (LeafPsiElement) PsiTreeUtil.getDeepestLast(this);
        }
        return literal;
    }

    /** 获取字面量的数据类型 */
    public PsiClass getDataType() {
        LeafPsiElement target = getLiteral();
        TokenIElementType token = (TokenIElementType) target.getElementType();

        return getPsiClassByToken(token);
    }
}
