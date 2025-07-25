/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.psi;

import java.util.ArrayList;
import java.util.List;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.XmlTextImpl;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.xml.XmlElementType.XML_CDATA;
import static com.intellij.psi.xml.XmlTokenType.XML_DATA_CHARACTERS;

/**
 * 节点中的文本节点
 * <p/>
 * 包含 CDATA 节点（{@link com.intellij.psi.xml.XmlElementType#XML_CDATA XML_CDATA}），
 * 并且，除了 CDATA 的文本是一个整体外，其余的文本会被拆分为空白和非空白两类 Token 作为
 * {@link XLangText} 的直接叶子节点：
 * <pre>
 * XLangText:XML_TEXT
 *   PsiElement(XML_CDATA)
 *     XmlToken:XML_CDATA_START('<![CDATA[')
 *     XLangTextToken:XML_DATA_CHARACTERS('\n        abc def\n    ')
 *     XmlToken:XML_CDATA_END(']]>')
 * </pre>
 * <pre>
 * XLangText:XML_TEXT
 *   PsiWhiteSpace('\n        ')
 *   XLangTextToken:XML_DATA_CHARACTERS('This')
 *   PsiWhiteSpace(' ')
 *   XLangTextToken:XML_DATA_CHARACTERS('is')
 *   PsiWhiteSpace(' ')
 *   XLangTextToken:XML_DATA_CHARACTERS('a')
 *   PsiWhiteSpace(' ')
 *   XmlToken:XML_CHAR_ENTITY_REF('&amp;lt;')
 *   XLangTextToken:XML_DATA_CHARACTERS('text/')
 *   XmlToken:XML_CHAR_ENTITY_REF('&amp;gt;')
 *   PsiWhiteSpace(' ')
 *   XLangTextToken:XML_DATA_CHARACTERS('node.')
 *   PsiWhiteSpace('\n        ')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-09
 */
public class XLangText extends XmlTextImpl {

    @Override
    public String toString() {
        return getClass().getSimpleName() + ':' + getElementType();
    }

    /** 获取文本内容（特殊符号已转义） */
    public @NotNull String getTextChars() {
        PsiElement cdata = findPsiChildByType(XML_CDATA);

        if (cdata != null) {
            ASTNode node = cdata.getNode().findChildByType(XML_DATA_CHARACTERS);
            return node != null ? node.getText() : "";
        }
        return getText();
    }

    public @NotNull XLangTextToken[] getTextTokens() {
        List<XLangTextToken> tokens = new ArrayList<>();

        for (PsiElement child : getChildren()) {
            if (child.getNode().getElementType() == XML_CDATA) {
                for (PsiElement c : child.getChildren()) {
                    if (c instanceof XLangTextToken token) {
                        tokens.add(token);
                    }
                }
            } //
            else if (child instanceof XLangTextToken token) {
                tokens.add(token);
            }
        }
        return tokens.toArray(new XLangTextToken[0]);
    }
}
