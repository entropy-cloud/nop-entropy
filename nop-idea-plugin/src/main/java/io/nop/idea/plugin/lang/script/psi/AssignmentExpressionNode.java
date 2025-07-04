package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

/**
 * 变量赋值 <code>xyz = "234";</code>：
 * <pre>
 * RuleSpecNode(assignmentExpression)
 *   RuleSpecNode(expression_leftHandSide)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('xyz')
 *   PsiWhiteSpace(' ')
 *   RuleSpecNode(assignmentOperator_)
 *     PsiElement('=')('=')
 *   PsiWhiteSpace(' ')
 *   ExpressionNode(expression_single)
 *     LiteralNode(literal)
 *       RuleSpecNode(literal_string)
 *         PsiElement(StringLiteral)('"234"')
 *   RuleSpecNode(eos__)
 *     PsiElement(';')(';')
 * </pre>
 *
 * 数组元素赋值 <code>arr[0] = 'a';</code>：
 * <pre>
 * AssignmentExpressionNode(assignmentExpression)
 *   RuleSpecNode(expression_leftHandSide)
 *     RuleSpecNode(memberExpression)
 *       ExpressionNode(expression_single)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('arr')
 *       PsiElement('[')('[')
 *       ExpressionNode(expression_single)
 *         LiteralNode(literal)
 *           RuleSpecNode(literal_numeric)
 *             PsiElement(DecimalIntegerLiteral)('0')
 *       PsiElement(']')(']')
 *   PsiWhiteSpace(' ')
 *   RuleSpecNode(assignmentOperator_)
 *     PsiElement('=')('=')
 *   PsiWhiteSpace(' ')
 *   ExpressionNode(expression_single)
 *     LiteralNode(literal)
 *       RuleSpecNode(literal_string)
 *         PsiElement(StringLiteral)(''a'')
 *   RuleSpecNode(eos__)
 *     PsiElement(';')(';')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-04
 */
public class AssignmentExpressionNode extends RuleSpecNode {
    private ExpressionNode expression;

    public AssignmentExpressionNode(@NotNull ASTNode node) {
        super(node);
    }

    public IdentifierNode getVarNameNode() {
        RuleSpecNode node = (RuleSpecNode) getFirstChild().getFirstChild();
        return node instanceof IdentifierNode ? (IdentifierNode) node : null;
    }

    public PsiClass getVarType() {
        if (expression == null || !expression.isValid()) {
            expression = findChildByClass(ExpressionNode.class);
        }
        return expression != null ? expression.getResultType() : null;
    }
}
