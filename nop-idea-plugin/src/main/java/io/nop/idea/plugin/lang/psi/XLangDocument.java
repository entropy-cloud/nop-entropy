package io.nop.idea.plugin.lang.psi;

import com.intellij.psi.impl.source.xml.XmlDocumentImpl;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangDocument extends XmlDocumentImpl {

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
