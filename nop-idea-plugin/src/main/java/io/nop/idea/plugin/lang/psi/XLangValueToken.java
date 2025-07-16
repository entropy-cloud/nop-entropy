package io.nop.idea.plugin.lang.psi;

import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * {@link XLangAttributeValue} 中不含引号的部分：
 * <pre>
 * XLangAttributeValue
 *   XmlToken:XML_ATTRIBUTE_VALUE_START_DELIMITER('"')
 *   XLangValueToken:XML_ATTRIBUTE_VALUE_TOKEN('/nop/schema/xdsl.xdef')
 *   XmlToken:XML_ATTRIBUTE_VALUE_END_DELIMITER('"')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangValueToken extends XmlTokenImpl {

    public XLangValueToken(@NotNull IElementType type, CharSequence text) {
        super(type, text);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ':' + getTokenType();
    }
}
