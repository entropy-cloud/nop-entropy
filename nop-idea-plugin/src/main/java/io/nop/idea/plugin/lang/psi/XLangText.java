package io.nop.idea.plugin.lang.psi;

import com.intellij.psi.impl.source.xml.XmlTextImpl;

/**
 * 节点中的文本节点
 * <p/>
 * 包含 CDATA 节点（{@link com.intellij.psi.xml.XmlElementType#XML_CDATA XML_CDATA}），
 * 并且，除了 CDATA 的文本是一个整体外，其余的文本会被拆分为空白和非空白两类 Token 作为
 * {@link XLangText} 的直接叶子节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangText extends XmlTextImpl {

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
