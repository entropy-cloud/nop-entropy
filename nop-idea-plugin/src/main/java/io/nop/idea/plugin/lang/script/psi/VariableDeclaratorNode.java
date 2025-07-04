package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import io.nop.idea.plugin.lang.XLangVarDecl;
import org.jetbrains.annotations.NotNull;

import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.RULE_ast_identifierOrPattern;
import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.RULE_expression_initializer;

/**
 * <code>abc = 123</code>：
 * <pre>
 * VariableDeclaratorNode(variableDeclarator)
 *   RuleSpecNode(ast_identifierOrPattern)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('abc')
 *   RuleSpecNode(expression_initializer)
 *     PsiElement('=')('=')
 *     ExpressionNode(expression_single)
 *       LiteralNode(literal)
 *         RuleSpecNode(literal_numeric)
 *           PsiElement(DecimalIntegerLiteral)('123')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-03
 */
public class VariableDeclaratorNode extends RuleSpecNode {
    private IdentifierNode identifier;
    private ExpressionNode expression;

    public VariableDeclaratorNode(@NotNull ASTNode node) {
        super(node);
    }

    protected IdentifierNode getIdentifier() {
        if (identifier == null || !identifier.isValid()) {
            RuleSpecNode node = findChildByType(RULE_ast_identifierOrPattern);

            identifier = node != null ? (IdentifierNode) node.getFirstChild() : null;
        }
        return identifier;
    }

    protected ExpressionNode getExpression() {
        if (expression == null || !expression.isValid()) {
            RuleSpecNode node = findChildByType(RULE_expression_initializer);

            expression = node != null ? (ExpressionNode) node.getLastChild() : null;
        }
        return expression;
    }

    @Override
    public @NotNull Map<String, XLangVarDecl> getVars() {
        IdentifierNode identifier = getIdentifier();
        ExpressionNode expression = getExpression();

        String varName = identifier != null ? identifier.getText() : null;
        PsiClass varType = expression != null ? expression.getResultType() : null;
        if (varName == null || varType == null) {
            return Map.of();
        }

        XLangVarDecl varDecl = new XLangVarDecl(varType, identifier);

        return Map.of(varName, varDecl);
    }
}
