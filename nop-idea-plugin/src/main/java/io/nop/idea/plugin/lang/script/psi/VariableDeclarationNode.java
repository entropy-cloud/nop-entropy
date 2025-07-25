/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import io.nop.idea.plugin.lang.XLangVarDecl;
import org.jetbrains.annotations.NotNull;

/**
 * 含 <code>const</code> 和 <code>let</code> 语句
 * <p/>
 * 多变量声明 <code>let abc = 123, def = 456;</code>：
 * <pre>
 * VariableDeclarationNode(variableDeclaration)
 *   RuleSpecNode(varModifier_)
 *     PsiElement('let')('let')
 *   VariableDeclaratorsNode(variableDeclarators_)
 *     VariableDeclaratorNode(variableDeclarator)
 *       RuleSpecNode(ast_identifierOrPattern)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('abc')
 *       RuleSpecNode(expression_initializer)
 *         PsiElement('=')('=')
 *         ExpressionNode(expression_single)
 *           LiteralNode(literal)
 *             RuleSpecNode(literal_numeric)
 *               PsiElement(DecimalIntegerLiteral)('123')
 *     PsiElement(',')(',')
 *     VariableDeclaratorNode(variableDeclarator)
 *       RuleSpecNode(ast_identifierOrPattern)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('def')
 *       RuleSpecNode(expression_initializer)
 *         PsiElement('=')('=')
 *         ExpressionNode(expression_single)
 *           ExpressionNode(expression_single)
 *             LiteralNode(literal)
 *               RuleSpecNode(literal_numeric)
 *                 PsiElement(DecimalIntegerLiteral)('456')
 *   RuleSpecNode(eos__)
 *     PsiElement(';')(';')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class VariableDeclarationNode extends RuleSpecNode {
    private VariableDeclaratorsNode declarators;

    public VariableDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, XLangVarDecl> getVars() {
        if (declarators == null) {
            declarators = findChildByClass(VariableDeclaratorsNode.class);
        }
        return declarators == null ? Map.of() : declarators.getVars();
    }
}
