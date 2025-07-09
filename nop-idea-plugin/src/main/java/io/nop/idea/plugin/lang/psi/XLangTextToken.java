package io.nop.idea.plugin.lang.psi;

import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.tree.IElementType;
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
}
