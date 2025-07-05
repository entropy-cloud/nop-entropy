package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * 对象的属性赋值节点
 * <p/>
 * 如 <code>{a, b: 1}</code> 中，<code>a</code> 和 <code>b: 1</code> 均为该类型节点
 * <p/>
 * <code>a</code>：
 * <pre>
 * ObjectPropertyAssignmentNode(propertyAssignment)
 *   ObjectMemberNode(identifier_ex)
 *     RuleSpecNode(identifierOrKeyword_)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('a')
 * </pre>
 *
 * <code>b: 1</code>：
 * <pre>
 * ObjectPropertyAssignmentNode(propertyAssignment)
 *   RuleSpecNode(expression_propName)
 *     ObjectMemberNode(identifier_ex)
 *       RuleSpecNode(identifierOrKeyword_)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('b')
 *   PsiElement(':')(':')
 *   PsiWhiteSpace(' ')
 *   ExpressionNode(expression_single)
 *     LiteralNode(literal)
 *       RuleSpecNode(literal_numeric)
 *         PsiElement(DecimalIntegerLiteral)('1')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class ObjectPropertyAssignmentNode extends RuleSpecNode {

    public ObjectPropertyAssignmentNode(@NotNull ASTNode node) {
        super(node);
    }

    public IdentifierNode getPropNameNode() {
        PsiElement child = getFirstChild();

        while (child != null) {
            if (child instanceof IdentifierNode i) {
                return i;
            }
            child = child.getFirstChild();
        }
        return null;
    }

    /** 是否为属性名与变量名相同时的简写形式 */
    public boolean isShorthand() {
        return getFirstChild() instanceof ObjectMemberNode;
    }
}
