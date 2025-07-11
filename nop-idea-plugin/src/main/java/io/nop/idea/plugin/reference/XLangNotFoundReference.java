package io.nop.idea.plugin.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import io.nop.idea.plugin.lang.reference.XLangReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * XLang 引用不存在
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-24
 */
public class XLangNotFoundReference extends PsiReferenceBase<XmlElement> implements XLangReference {
    private final String message;

    public XLangNotFoundReference(@NotNull XmlElement refElement, TextRange textRange, String message) {
        super(refElement, textRange, false);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return null;
    }

    @Override
    public @NotNull Object @NotNull [] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement target) {
        return false;
    }
}
