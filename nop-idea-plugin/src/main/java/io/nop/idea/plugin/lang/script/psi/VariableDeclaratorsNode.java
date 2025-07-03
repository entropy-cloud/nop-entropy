package io.nop.idea.plugin.lang.script.psi;

import java.util.HashMap;
import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * 由逗号分隔的多个变量声明的节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-03
 */
public class VariableDeclaratorsNode extends RuleSpecNode {

    public VariableDeclaratorsNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, VarDecl> getVarTypes() {
        /* 变量声明：abc = 123, def = 456;
        VariableDeclaratorsNode(variableDeclarators_)
          VariableDeclaratorNode(variableDeclarator)
            RuleSpecNode(ast_identifierOrPattern)
              IdentifierNode(identifier)
                PsiElement(Identifier)('abc')
            RuleSpecNode(expression_initializer)
              PsiElement('=')('=')
              ExpressionNode(expression_single)
                LiteralNode(literal)
                  RuleSpecNode(literal_numeric)
                    PsiElement(DecimalIntegerLiteral)('123')
        */
        Map<String, VarDecl> vars = new HashMap<>();

        for (PsiElement child : getChildren()) {
            if (child instanceof VariableDeclaratorNode declarator) {
                vars.putAll(declarator.getVarTypes());
            }
        }
        return vars;
    }
}
