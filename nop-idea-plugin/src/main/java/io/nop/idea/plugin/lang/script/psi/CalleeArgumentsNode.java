package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

/**
 * 函数调用的参数列表节点
 * <p/>
 * 参数列表 <code>(1, 2)</code>：
 * <pre>
 * CalleeArgumentsNode(arguments_)
 *   PsiElement('(')('(')
 *   ExpressionNode(expression_single)
 *     LiteralNode(literal)
 *       RuleSpecNode(literal_numeric)
 *         PsiElement(DecimalIntegerLiteral)('1')
 *   PsiElement(',')(',')
 *   PsiWhiteSpace(' ')
 *   ExpressionNode(expression_single)
 *     LiteralNode(literal)
 *       RuleSpecNode(literal_numeric)
 *         PsiElement(DecimalIntegerLiteral)('2')
 *   PsiElement(')')(')')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class CalleeArgumentsNode extends RuleSpecNode {

    public CalleeArgumentsNode(@NotNull ASTNode node) {
        super(node);
    }

    public PsiClass @NotNull [] getArgumentTypes() {
        ExpressionNode[] exprs = findChildrenByClass(ExpressionNode.class);

        PsiClass[] types = new PsiClass[exprs.length];
        for (int i = 0; i < exprs.length; i++) {
            ExpressionNode expr = exprs[i];

            types[i] = expr.getResultType();
        }
        return types;
    }
}
