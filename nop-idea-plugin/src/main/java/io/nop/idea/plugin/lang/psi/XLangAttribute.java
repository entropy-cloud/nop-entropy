package io.nop.idea.plugin.lang.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import com.intellij.psi.xml.XmlElement;
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
        XmlElement attrName = getNameElement();
        if (attrName == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        TextRange textRange = attrName.getTextRangeInParent();
        XLangAttributeReference ref = new XLangAttributeReference(this, textRange);

        //return super.getReferences(hints);
        return new PsiReference[] { ref };
    }

    /** 获取当前属性的 xdef 定义 */
    public IXDefAttribute getXDefAttr() {
        XLangTag tag = (XLangTag) getParent();
        if (tag == null) {
            return null;
        }

        // - 对于声明属性，从其自身（*.xdef）中取其定义
        // - 对于赋值属性，从其 x:schema 中取其定义
        // - 对于名字空间对应 xdsl.xdef 和 xdef.xdef 的属性，则分别从这两个元模型中取属性定义
        IXDefAttribute attrDef;

        String attrName = getName();
        String xdefNs = tag.getXDefNs();
        String xdslNs = tag.getXDslNs();

        if (tag.isInXDef()) {
            // 取 xdef.xdef 中声明的属性
            if (attrName.startsWith(xdefNs + ':')) {
                attrDef = tag.getXDefNodeAttr(attrName);
            }
            // 取 xdsl.xdef 中声明的属性
            else if (attrName.startsWith(xdslNs + ':')) {
                attrDef = tag.getXDslDefNodeAttr(attrName);
            }
            // 取自身声明的属性
            else {
                attrDef = tag.getSelfDefNodeAttr(attrName);
            }
        } else {
            if (attrName.startsWith(xdslNs + ':')) {
                attrDef = tag.getXDslDefNodeAttr(attrName);
            } else {
                attrDef = tag.getXDefNodeAttr(attrName);
            }
        }

        return attrDef;
    }
}
