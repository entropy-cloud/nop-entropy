package io.nop.idea.plugin.lang.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link io.nop.xlang.xdef.XDefKeys#KEY_ATTR xdef:key-attr} 的值引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-13
 */
public class XLangXdefKeyAttrReference extends XLangReferenceBase implements PsiPolyVariantReference {
    private final String attrValue;

    public XLangXdefKeyAttrReference(XmlElement myElement, TextRange myRangeInElement, String attrValue) {
        super(myElement, myRangeInElement);
        this.attrValue = attrValue;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        ResolveResult[] results = multiResolve(false);

        if (results.length == 0) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.xdef-key-attr-not-found", attrValue);
            setUnresolvedMessage(msg);
        }

        return results.length == 1 ? results[0].getElement() : null;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        XLangTag tag = PsiTreeUtil.getParentOfType(myElement, XLangTag.class);
        if (tag == null) {
            return ResolveResult.EMPTY_ARRAY;
        }

        return XmlPsiHelper.getAttrsFromChildTag(tag, attrValue).stream() //
                           .map(PsiElementResolveResult::new) //
                           .toArray(ResolveResult[]::new);
    }
}
