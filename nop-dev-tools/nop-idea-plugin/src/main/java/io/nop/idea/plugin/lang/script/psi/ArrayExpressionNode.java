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
import org.jetbrains.annotations.NotNull;

/**
 * <code>[a, b, c]</code>：
 * <pre>
 * RuleSpecNode(arrayExpression)
 *   PsiElement('[')('[')
 *   RuleSpecNode(elementList_)
 *     RuleSpecNode(ast_arrayElement)
 *       ExpressionNode(expression_single)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('a')
 *     PsiElement(',')(',')
 *     PsiWhiteSpace(' ')
 *     RuleSpecNode(ast_arrayElement)
 *       ExpressionNode(expression_single)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('b')
 *     PsiElement(',')(',')
 *     PsiWhiteSpace(' ')
 *     RuleSpecNode(ast_arrayElement)
 *       ExpressionNode(expression_single)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('c')
 *   PsiElement(']')(']')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-04
 */
public class ArrayExpressionNode extends RuleSpecNode {

    public ArrayExpressionNode(@NotNull ASTNode node) {
        super(node);
    }

    /** 获取数组元素类型 */
    public PsiClass getElementType() {
        // TODO 返回第一个不为 null 的元素类型
        return null;
    }
}
