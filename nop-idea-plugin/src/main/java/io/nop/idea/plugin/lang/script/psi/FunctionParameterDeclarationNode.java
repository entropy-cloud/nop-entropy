package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReference;
import io.nop.api.core.util.Symbol;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.NotNull;

import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.RULE_namedTypeNode_annotation;

/**
 * 函数参数定义节点
 * <p/>
 * <pre>
 * FunctionParameterDeclarationNode(parameterDeclaration)
 *   RuleSpecNode(ast_identifierOrPattern)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('a')
 * </pre>
 *
 * <pre>
 * FunctionParameterDeclarationNode(parameterDeclaration)
 *   RuleSpecNode(ast_identifierOrPattern)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('a')
 *   RuleSpecNode(namedTypeNode_annotation)
 *     PsiElement(':')(':')
 *     PsiWhiteSpace(' ')
 *     RuleSpecNode(namedTypeNode)
 *       RuleSpecNode(typeNameNode_predefined)
 *         PsiElement('string')('string')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class FunctionParameterDeclarationNode extends RuleSpecNode {
    private IdentifierNode parameterName;

    public FunctionParameterDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }

    public IdentifierNode getParameterName() {
        if (parameterName == null || !parameterName.isValid()) {
            parameterName = (IdentifierNode) getFirstChild().getFirstChild();
        }
        return parameterName;
    }

    public PsiClass getParameterType() {
        PsiClassAndTextRange result = getClassAndTextRanges();

        return result == null ? null : result.clazz();
    }

    @Override
    protected PsiReference @NotNull [] doGetReferences() {
        PsiClassAndTextRange result = getClassAndTextRanges();
        if (result == null || result.clazz() == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        return new PsiReference[] {
                PsiClassAndTextRange.createReference(this, result)
        };
    }

    protected PsiClassAndTextRange getClassAndTextRanges() {
        RuleSpecNode typeNode = findChildByType(RULE_namedTypeNode_annotation);
        if (typeNode == null) {
            return null;
        }

        // 仅包含确定类型，详见 nop-xlang/model/antlr/XLangTypeSystem.g4
        RuleSpecNode typeNameNode = (RuleSpecNode) typeNode.getLastChild();
        String typeName = typeNameNode.getText();

        Class<?> typeClass = switch (typeName) {
            case "any" -> Object.class;
            case "number" -> Number.class;
            case "boolean" -> Boolean.class;
            case "string" -> String.class;
            case "symbol" -> Symbol.class;
            default -> null;
        };

        PsiClass clazz = typeClass != null ? PsiClassHelper.findClass(getProject(), typeClass.getName()) : null;
        TextRange textRange = typeNameNode.getTextRangeInParent().shiftRight(typeNode.getStartOffsetInParent());

        return new PsiClassAndTextRange(clazz, textRange);
    }
}
