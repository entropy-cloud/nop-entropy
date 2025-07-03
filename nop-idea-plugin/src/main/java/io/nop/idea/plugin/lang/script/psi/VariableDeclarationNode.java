package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 含 <code>const</code> 和 <code>let</code> 语句
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class VariableDeclarationNode extends RuleSpecNode {

    public VariableDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, VarDecl> getVarTypes() {
        /* 变量声明：let def = 123;
          VariableDeclarationNode(variableDeclaration)
            RuleSpecNode(varModifier_)
              PsiElement('let')('let')
            VariableDeclaratorsNode(variableDeclarators_)
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
            RuleSpecNode(eos__)
              PsiElement(';')(';')
        */
        VariableDeclaratorsNode declarators = findChildByClass(VariableDeclaratorsNode.class);

        return declarators.getVarTypes();
    }
}
