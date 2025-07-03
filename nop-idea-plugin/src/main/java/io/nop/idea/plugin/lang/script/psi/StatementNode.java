package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
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
        PsiElement firstChild = getFirstChild();

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
        if (firstChild instanceof VariableDeclarationNode declaration) {
            RuleSpecNode declarator = (RuleSpecNode) declaration.getLastChild().getFirstChild();
            IdentifierNode identifier = (IdentifierNode) declarator.getFirstChild().getFirstChild();
            ExpressionNode expression = (ExpressionNode) declarator.getLastChild().getLastChild();

            String varName = identifier.getText();
            PsiClass varType = expression.getResultType();
            VarDecl varDecl = new VarDecl(identifier, varType);

            return Map.of(varName, varDecl);
        }

        return Map.of();
    }
}
