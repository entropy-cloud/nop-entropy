package io.nop.idea.plugin.lang.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.lang.reference.XLangReferenceHelper;
import io.nop.idea.plugin.lang.reference.XLangXdefKeyAttrReference;
import io.nop.idea.plugin.lang.reference.XLangParentTagAttrReference;
import io.nop.idea.plugin.lang.reference.XLangXPrototypeReference;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdsl.XDslKeys;
import org.jetbrains.annotations.NotNull;

/**
 * 属性值，由引号和 {@link XLangValueToken} 组成
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangAttributeValue extends XmlAttributeValueImpl {

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public PsiReference @NotNull [] getReferences(@NotNull PsiReferenceService.Hints hints) {
        String attrValue = getValue();
        if (StringHelper.isEmpty(attrValue)) {
            return PsiReference.EMPTY_ARRAY;
        }

        if (!(getParent() instanceof XLangAttribute attr)) {
            return PsiReference.EMPTY_ARRAY;
        }

        IXDefAttribute attrDef = attr.getXDefAttr();
        if (attrDef == null) {
            return XLangReferenceHelper.getReferencesFromText(this, attrValue);
        }

        XDefTypeDecl attrDefType = attrDef.getType();
        // 对于声明属性（定义属性名及其类型），仅对其类型的定义（涉及枚举和字典）做引用识别
        if (attr.isDeclaredDefAttr(attrDef)) {
            return XLangReferenceHelper.getReferencesFromDefType(this, attrValue, attrDefType);
        }

        // 根据属性的类型，对属性值做文件/名字引用
        PsiReference[] refs = XLangReferenceHelper.getReferencesByStdDomain(this,
                                                                            attrValue,
                                                                            attrDefType.getStdDomain());
        if (refs != null) {
            return refs;
        }

        XLangTag tag = (XLangTag) attr.getParent();
        XDslKeys xdslKeys = tag.getXDslKeys();
        XDefKeys xdefKeys = tag.getXDefKeys();

        String attrName = attr.getName();
        // Note: XmlAttributeValue 的文本范围是包含引号的
        TextRange attrValueTextRange = getValueTextRange().shiftLeft(getStartOffset());
        if (xdslKeys.PROTOTYPE.equals(attrName)) {
            return new PsiReference[] {
                    new XLangXPrototypeReference(this, attrValueTextRange, attrValue)
            };
        } //
        else if (xdefKeys.KEY_ATTR.equals(attrName)) {
            return new PsiReference[] {
                    new XLangXdefKeyAttrReference(this, attrValueTextRange, attrValue)
            };
        } //
        else if (xdefKeys.UNIQUE_ATTR.equals(attrName)) {
            return new PsiReference[] {
                    new XLangParentTagAttrReference(this, attrValueTextRange, attrValue)
            };
        }

        // TODO 其他引用识别
        // <c:import from="/nop/web/xlib/web.xlib" />
        // <c:include src="dingflow-gen/impl_GenComponents.xpl" />
        return XLangReferenceHelper.getReferencesFromText(this, attrValue);
    }
}
