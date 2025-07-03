package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-03
 */
public class StatementNode extends RuleSpecNode {

    public StatementNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, VarDecl> getVarTypes() {
        /* 变量声明：let def = 123;
        StatementNode(statement)
          VariableDeclarationNode(variableDeclaration)
            RuleSpecNode(varModifier_)
              PsiElement('let')('let')
            RuleSpecNode(variableDeclarators_)
              RuleSpecNode(variableDeclarator)
                RuleSpecNode(ast_identifierOrPattern)
                  IdentifierNode(identifier)
                    PsiElement(Identifier)('def')
                RuleSpecNode(expression_initializer)
                  PsiElement('=')('=')
                  ExpressionNode(expression_single)
                    LiteralNode(literal)
                      RuleSpecNode(literal_numeric)
                        PsiElement(DecimalIntegerLiteral)('123')
            RuleSpecNode(eos__)
              PsiElement(';')(';')
        */
        for (PsiElement child : getChildren()) {
            if (child instanceof VariableDeclarationNode declaration) {
                return declaration.getVarTypes();
            }
        }
        return Map.of();
    }
}
