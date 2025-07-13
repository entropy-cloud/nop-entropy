package io.nop.idea.plugin.lang.script.psi;

import java.util.ArrayList;
import java.util.List;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import io.nop.idea.plugin.lang.script.reference.IdentifierReference;
import org.jetbrains.annotations.NotNull;

import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.RULE_objectProperties;

/**
 * 对象声明节点
 * <p/>
 * <code>{a, b: 1}</code>：
 * <pre>
 * ObjectDeclarationNode(objectExpression)
 *   PsiElement('{')('{')
 *   RuleSpecNode(objectProperties_)
 *     ObjectPropertyDeclarationNode(ast_objectProperty)
 *       ObjectPropertyAssignmentNode(propertyAssignment)
 *         ObjectMemberNode(identifier_ex)
 *           RuleSpecNode(identifierOrKeyword_)
 *             IdentifierNode(identifier)
 *               PsiElement(Identifier)('a')
 *     PsiElement(',')(',')
 *     PsiWhiteSpace(' ')
 *     ObjectPropertyDeclarationNode(ast_objectProperty)
 *       ObjectPropertyAssignmentNode(propertyAssignment)
 *         RuleSpecNode(expression_propName)
 *           ObjectMemberNode(identifier_ex)
 *             RuleSpecNode(identifierOrKeyword_)
 *               IdentifierNode(identifier)
 *                 PsiElement(Identifier)('b')
 *         PsiElement(':')(':')
 *         PsiWhiteSpace(' ')
 *         ExpressionNode(expression_single)
 *           LiteralNode(literal)
 *             RuleSpecNode(literal_numeric)
 *               PsiElement(DecimalIntegerLiteral)('1')
 *   PsiElement('}')('}')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-02
 */
public class ObjectDeclarationNode extends RuleSpecNode {

    public ObjectDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    protected PsiReference @NotNull [] doGetReferences() {
        RuleSpecNode props = findChildByType(RULE_objectProperties);
        if (props == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        List<PsiReference> result = new ArrayList<>();
        for (PsiElement child : props.getChildren()) {
            if (!(child instanceof ObjectPropertyDeclarationNode propDecl)) {
                continue;
            }

            ObjectPropertyAssignmentNode prop = (ObjectPropertyAssignmentNode) propDecl.getFirstChild();
            if (!prop.isShorthand()) {
                continue;
            }

            TextRange textRange = propDecl.getTextRangeInParent();
            IdentifierNode propNameNode = prop.getPropNameNode();

            IdentifierReference ref = new IdentifierReference(this, textRange, propNameNode);

            result.add(ref);
        }

        return result.toArray(PsiReference.EMPTY_ARRAY);
    }
}
