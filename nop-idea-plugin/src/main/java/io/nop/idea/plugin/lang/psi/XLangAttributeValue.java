package io.nop.idea.plugin.lang.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.lang.reference.XLangParentTagAttrReference;
import io.nop.idea.plugin.lang.reference.XLangReferenceHelper;
import io.nop.idea.plugin.lang.reference.XLangXPrototypeReference;
import io.nop.idea.plugin.lang.reference.XLangXdefKeyAttrReference;
import io.nop.idea.plugin.lang.reference.XLangXdefNameReference;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdsl.XDslKeys;
import org.jetbrains.annotations.NotNull;

/**
 * 属性值，由引号和 {@link XLangValueToken} 组成：
 * <pre>
 * XLangAttributeValue
 *   XmlToken:XML_ATTRIBUTE_VALUE_START_DELIMITER('"')
 *   XLangValueToken:XML_ATTRIBUTE_VALUE_TOKEN('Child ')
 *   XmlToken:XML_CHAR_ENTITY_REF('&amp;amp;')
 *   XLangValueToken:XML_ATTRIBUTE_VALUE_TOKEN(' Tag')
 *   XmlToken:XML_ATTRIBUTE_VALUE_END_DELIMITER('"')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangAttributeValue extends XmlAttributeValueImpl {

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public XLangAttribute getParentAttr() {
        return getParent() instanceof XLangAttribute p ? p : null;
    }

    @Override
    public PsiReference @NotNull [] getReferences(@NotNull PsiReferenceService.Hints hints) {
        String attrValue = getValue();
        if (StringHelper.isEmpty(attrValue)) {
            return PsiReference.EMPTY_ARRAY;
        }

        XLangAttribute attr = getParentAttr();
        if (attr == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        IXDefAttribute attrDef = attr.getDefAttr();
        // 对于未定义属性，不做引用识别
        if (attrDef == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        // 根据属性名，从属性值中查找引用
        PsiReference[] refs = getReferencesByAttrName(attr, attrValue);
        if (refs != null) {
            return refs;
        }

        // 根据属性定义类型，从属性值中查找引用
        refs = XLangReferenceHelper.getReferencesByDefType(this, attrValue, attrDef.getType());
        if (refs != null) {
            return refs;
        }

        // TODO 其他引用识别
        // <c:import from="/nop/web/xlib/web.xlib" />
        // <c:include src="dingflow-gen/impl_GenComponents.xpl" />
        return XLangReferenceHelper.getReferencesFromText(this, attrValue);
    }

    private PsiReference[] getReferencesByAttrName(XLangAttribute attr, String attrValue) {
        XLangTag tag = attr.getParentTag();
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
        else if (xdefKeys.UNIQUE_ATTR.equals(attrName) //
                 || xdefKeys.ORDER_ATTR.equals(attrName) //
        ) {
            return new PsiReference[] {
                    new XLangParentTagAttrReference(this, attrValueTextRange, attrValue)
            };
        } //
        else if (xdefKeys.NAME.equals(attrName)) {
            // 与根节点上的 xdef:bean-package 组成 class
            XLangTag rootTag = tag.getRootTag();

            String pkgName = rootTag.getAttributeValue(rootTag.getXDefKeys().BEAN_PACKAGE);
            if (StringHelper.isEmpty(pkgName)) {
                return PsiReference.EMPTY_ARRAY;
            }

            return new PsiReference[] {
                    new XLangXdefNameReference(this, attrValueTextRange, pkgName, attrValue)
            };
        }

        return null;
    }
}
