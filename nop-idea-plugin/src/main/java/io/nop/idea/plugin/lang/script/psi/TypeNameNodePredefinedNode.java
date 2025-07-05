package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import io.nop.api.core.util.Symbol;
import io.nop.idea.plugin.lang.script.reference.PsiClassReference;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.NotNull;

/**
 * 预定义类型节点
 * <p/>
 * <pre>
 * RuleSpecNode(typeNameNode_predefined)
 *   PsiElement('string')('string')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-05
 */
public class TypeNameNodePredefinedNode extends RuleSpecNode {
    private PsiElement typeName;

    public TypeNameNodePredefinedNode(@NotNull ASTNode node) {
        super(node);
    }

    public PsiElement getTypeName() {
        if (typeName == null || !typeName.isValid()) {
            typeName = getLastChild();
        }
        return typeName;
    }

    public PsiClass getPredefinedType() {
        // 仅包含确定类型，详见 nop-xlang/model/antlr/XLangTypeSystem.g4
        PsiElement typeNameNode = getTypeName();
        String typeName = typeNameNode.getText();

        Class<?> typeClass = switch (typeName) {
            case "any" -> Object.class;
            case "number" -> Number.class;
            case "boolean" -> Boolean.class;
            case "string" -> String.class;
            case "symbol" -> Symbol.class;
            case "void" -> Void.class;
            default -> null;
        };

        return typeClass != null ? PsiClassHelper.findClass(getProject(), typeClass.getName()) : null;
    }

    @Override
    protected PsiReference @NotNull [] doGetReferences() {
        PsiClass clazz = getPredefinedType();
        if (clazz == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        PsiElement typeNameNode = getTypeName();

        return new PsiReference[] {
                new PsiClassReference(this, clazz, typeNameNode.getTextRangeInParent())
        };
    }
}
