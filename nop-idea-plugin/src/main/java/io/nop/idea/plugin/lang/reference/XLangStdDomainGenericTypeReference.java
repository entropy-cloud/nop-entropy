package io.nop.idea.plugin.lang.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.nop.core.type.IGenericType;
import io.nop.core.type.parse.GenericTypeParser;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.Nullable;

/**
 * {@link io.nop.xlang.xdef.XDefConstants#STD_DOMAIN_GENERIC_TYPE generic-type} 类型的值引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-15
 */
public class XLangStdDomainGenericTypeReference extends XLangReferenceBase {
    private final String attrValue;

    public XLangStdDomainGenericTypeReference(PsiElement myElement, TextRange myRangeInElement, String attrValue) {
        super(myElement, myRangeInElement);
        this.attrValue = attrValue;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        IGenericType type = null;
        try {
            type = new GenericTypeParser().parseFromText(null, attrValue);
        } catch (Exception ignore) {
        }

        if (type == null) {
            return null;
        }

        return PsiClassHelper.findClass(myElement, type.getClassName());
    }
}
