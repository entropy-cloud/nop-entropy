package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-03
 */
public class VariableDeclaratorNode extends RuleSpecNode {

    public VariableDeclaratorNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, VarDecl> getVarTypes() {
        /* 变量声明：def = 123;
          VariableDeclaratorNode(variableDeclarator)
            RuleSpecNode(ast_identifierOrPattern)
              IdentifierNode(identifier)
                PsiElement(Identifier)('def')
            RuleSpecNode(expression_initializer)
              PsiElement('=')('=')
              ExpressionNode(expression_single)
                LiteralNode(literal)
                  RuleSpecNode(literal_numeric)
                    PsiElement(DecimalIntegerLiteral)('123')
        */
        IdentifierNode identifier = ((RuleSpecNode) getFirstChild()).getIdentifier();
        ExpressionNode expression = (ExpressionNode) getLastChild().getLastChild();

        String varName = identifier.getText();
        PsiClass varType = expression.getResultType();
        VarDecl varDecl = new VarDecl(identifier, varType);

        return Map.of(varName, varDecl);
    }
}
