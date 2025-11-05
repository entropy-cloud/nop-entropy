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
 * 箭头函数节点
 * <p/>
 * <code>(a, b) => a + b</code>：
 * <pre>
 * ArrowFunctionNode(arrowFunctionExpression)
 *   PsiElement('(')('(')
 *   FunctionParameterListNode(parameterList_)
 *     FunctionParameterDeclarationNode(parameterDeclaration)
 *       RuleSpecNode(ast_identifierOrPattern)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('a')
 *     PsiElement(',')(',')
 *     PsiWhiteSpace(' ')
 *     FunctionParameterDeclarationNode(parameterDeclaration)
 *       RuleSpecNode(ast_identifierOrPattern)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('b')
 *   PsiElement(')')(')')
 *   PsiWhiteSpace(' ')
 *   PsiElement('=>')('=>')
 *   PsiWhiteSpace(' ')
 *   ArrowFunctionBodyNode(expression_functionBody)
 *     ExpressionNode(expression_single)
 *       ExpressionNode(expression_single)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('a')
 *       PsiWhiteSpace(' ')
 *       PsiElement('+')('+')
 *       PsiWhiteSpace(' ')
 *       ExpressionNode(expression_single)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('b')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class ArrowFunctionNode extends RuleSpecNode {

    public ArrowFunctionNode(@NotNull ASTNode node) {
        super(node);
    }

    /** 获取函数的返回值类型 */
    public PsiClass getReturnType() {
        // TODO 分析 return 表达式，得到返回类型
        return null;
    }
}
