package io.nop.idea.plugin.lang.script.reference;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import io.nop.api.core.util.Symbol;
import io.nop.idea.plugin.lang.reference.XLangReferenceBase;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 对 <code>string</code>、<code>number</code> 等内置类型的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-06
 */
public class PredefinedTypeReference extends XLangReferenceBase {
    private final PsiElement typeName;

    public PredefinedTypeReference(PsiElement myElement, PsiElement typeName, TextRange myRangeInElement) {
        super(myElement, myRangeInElement);
        this.typeName = typeName;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        if (!typeName.isValid()) {
            return null;
        }

        Project project = myElement.getProject();

        return getPredefinedType(project, typeName.getText());
    }

    public static PsiClass getPredefinedType(Project project, String typeName) {
        // 仅包含确定类型，详见 nop-xlang/model/antlr/XLangTypeSystem.g4
        Class<?> typeClass = switch (typeName) {
            case "any" -> Object.class;
            case "number" -> Number.class;
            case "boolean" -> Boolean.class;
            case "string" -> String.class;
            case "symbol" -> Symbol.class;
            case "void" -> Void.class;
            default -> null;
        };

        return typeClass != null ? PsiClassHelper.findClass(project, typeClass.getName()) : null;
    }
}
