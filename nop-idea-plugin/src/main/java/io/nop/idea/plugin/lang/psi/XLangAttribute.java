package io.nop.idea.plugin.lang.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import io.nop.idea.plugin.lang.reference.XLangAttributeReference;
import io.nop.xlang.xdef.IXDefAttribute;
import org.jetbrains.annotations.NotNull;

/**
 * 属性，由名字（含名字空间）、等号和 {@link XLangAttributeValue} 组成
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangAttribute extends XmlAttributeImpl {

    @Override
    public String toString() {
        return getClass().getSimpleName() + ':' + getElementType() + "('" + getName() + "')";
    }

    @Override
    public PsiReference @NotNull [] getReferences(@NotNull PsiReferenceService.Hints hints) {
        // 参考 XmlAttributeDelegate#getDefaultReferences
        String ns = getNamespacePrefix();
        String name = getLocalName();

        if (name.isEmpty() || isNamespaceDeclaration()) {
            return super.getReferences(hints);
        }

        // 保留对名字空间的引用，以支持对其做高亮、重命名等
        SchemaPrefixReference ref0 = !ns.isEmpty()
                                     ? new SchemaPrefixReference(this, TextRange.allOf(ns), ns, null)
                                     : null;

        int nameOffset = (ns.isEmpty() ? -1 : ns.length()) + 1;
        TextRange nameTextRange = TextRange.allOf(name).shiftRight(nameOffset);
        XLangAttributeReference ref1 = new XLangAttributeReference(this, nameTextRange);

        return ref0 != null ? new PsiReference[] { ref0, ref1 } : new PsiReference[] { ref1 };
    }

    /** 获取当前属性的 xdef 定义 */
    public IXDefAttribute getXDefAttr() {
        XLangTag tag = (XLangTag) getParent();
        if (tag == null) {
            return null;
        }

        // - 对于声明属性（定义属性名及其类型），从其自身（*.xdef）中取其定义
        // - 对于赋值属性（为具体属性赋予相应类型的值），从其 x:schema 中取其定义
        // - 对于名字空间对应 xdsl.xdef 和 xdef.xdef 的属性，则分别从这两个元模型中取属性定义
        IXDefAttribute attrDef;

        String ns = getNamespacePrefix();
        String attrName = getName();
        boolean hasXDefNs = !ns.isEmpty() && ns.equals(tag.getXDefNs());
        boolean hasXDslNs = !ns.isEmpty() && ns.equals(tag.getXDslNs());

        // 取 xdsl.xdef 中声明的属性
        if (hasXDslNs) {
            attrDef = tag.getXDslDefNodeAttr(attrName);
        } //
        else if (tag.isInXDef()) {
            // 取 xdef.xdef 中声明的属性
            if (hasXDefNs) {
                attrDef = tag.getXDefNodeAttr(attrName);
            }
            // 取自身声明的属性
            else {
                attrDef = tag.getSelfDefNodeAttr(attrName);
            }
        } else {
            attrDef = tag.getXDefNodeAttr(attrName);
        }

        return attrDef;
    }

    /** 是否为当前属性自身的 xdef 定义 */
    public boolean isSelfDefAttr(IXDefAttribute attr) {
        String attrName = getName();
        XLangTag tag = (XLangTag) getParent();

        return tag != null && attr == tag.getSelfDefNodeAttr(attrName);
    }
}
