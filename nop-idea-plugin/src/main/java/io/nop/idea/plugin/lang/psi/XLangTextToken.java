package io.nop.idea.plugin.lang.psi;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.lang.reference.XLangReferenceHelper;
import io.nop.xlang.xdef.XDefTypeDecl;
import org.jetbrains.annotations.NotNull;

/**
 * CDATA 节点（{@link com.intellij.psi.xml.XmlElementType#XML_CDATA XML_CDATA}）中的全部文本，
 * 或者 {@link XLangText} 节点中的非空白文本
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangTextToken extends XmlTokenImpl {

    public XLangTextToken(@NotNull IElementType type, CharSequence text) {
        super(type, text);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ':' + getTokenType();
    }

    public XLangTag getParentTag() {
        return PsiTreeUtil.getParentOfType(this, XLangTag.class);
    }

    @Override
    public PsiReference @NotNull [] getReferences(PsiReferenceService.Hints hints) {
        XLangTag tag = getParentTag();
        if (tag == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        String text = getText();
        XDefTypeDecl xdefValue = tag.getDefNodeXdefValue();
        if (xdefValue == null || StringHelper.isBlank(text)) {
            return PsiReference.EMPTY_ARRAY;
        }

        PsiReference[] refs = XLangReferenceHelper.getReferencesByDefType(this, text, xdefValue);

        return refs != null ? refs : PsiReference.EMPTY_ARRAY;
    }
}
