package io.nop.idea.plugin.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlElement;
import io.nop.idea.plugin.lang.reference.XLangReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对 XLang 节点或属性等元素的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-23
 */
public class XLangElementReference extends PsiReferenceBase<XmlElement> implements XLangReference {
    private final PsiElement target;

    public XLangElementReference(@NotNull XmlElement element, TextRange rangeInElement, PsiElement target) {
        super(element, rangeInElement);
        this.target = target;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return target;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement target) {
        // XmlAttributeReference#isReferenceTo
        PsiManager manager = getElement().getManager();
        return manager.areElementsEquivalent(target, this.target);
    }
}
