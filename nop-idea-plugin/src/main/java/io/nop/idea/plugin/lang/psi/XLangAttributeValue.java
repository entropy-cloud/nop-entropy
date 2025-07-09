package io.nop.idea.plugin.lang.psi;

import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl;

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
}
