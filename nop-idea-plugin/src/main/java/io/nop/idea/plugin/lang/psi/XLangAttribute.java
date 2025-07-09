package io.nop.idea.plugin.lang.psi;

import com.intellij.psi.impl.source.xml.XmlAttributeImpl;

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
}
