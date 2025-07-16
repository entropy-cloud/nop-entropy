/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.psi;

import java.util.HashMap;
import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import io.nop.idea.plugin.lang.XLangVarDecl;
import org.jetbrains.annotations.NotNull;

/**
 * 由逗号分隔的多个变量声明的节点
 * <p/>
 * 如 <code>abc = 123, def = 456</code>：
 * <pre>
 * VariableDeclaratorsNode(variableDeclarators_)
 *   VariableDeclaratorNode(variableDeclarator)
 *     RuleSpecNode(ast_identifierOrPattern)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('abc')
 *     RuleSpecNode(expression_initializer)
 *       PsiElement('=')('=')
 *       ExpressionNode(expression_single)
 *         LiteralNode(literal)
 *           RuleSpecNode(literal_numeric)
 *             PsiElement(DecimalIntegerLiteral)('123')
 *   PsiElement(',')(',')
 *   VariableDeclaratorNode(variableDeclarator)
 *     RuleSpecNode(ast_identifierOrPattern)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('def')
 *     RuleSpecNode(expression_initializer)
 *       PsiElement('=')('=')
 *       ExpressionNode(expression_single)
 *         ExpressionNode(expression_single)
 *           LiteralNode(literal)
 *             RuleSpecNode(literal_numeric)
 *               PsiElement(DecimalIntegerLiteral)('456')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-03
 */
public class VariableDeclaratorsNode extends RuleSpecNode {

    public VariableDeclaratorsNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, XLangVarDecl> getVars() {
        Map<String, XLangVarDecl> vars = new HashMap<>();

        for (PsiElement child : getChildren()) {
            if (child instanceof VariableDeclaratorNode declarator) {
                vars.putAll(declarator.getVars());
            }
        }
        return vars;
    }
}
