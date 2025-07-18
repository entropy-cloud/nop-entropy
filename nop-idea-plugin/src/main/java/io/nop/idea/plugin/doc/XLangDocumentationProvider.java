/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.doc;

import java.util.Objects;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.core.dict.DictProvider;
import io.nop.idea.plugin.lang.XLangDocumentation;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.utils.MarkdownHelper;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlTagInfo;
import io.nop.xlang.xdef.XDefTypeDecl;
import jakarta.annotation.Nullable;

import static com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;
import static com.intellij.psi.xml.XmlTokenType.XML_NAME;

public class XLangDocumentationProvider extends AbstractDocumentationProvider {

    /**
     * 文档生成函数
     * <p/>
     * 默认鼠标移动时的文档也由该函数生成 {@link #generateHoverDoc}
     *
     * @param srcElement
     *         当前鼠标下的元素，对于 xml 标签和属性，其为
     *         {@link com.intellij.psi.xml.XmlTokenType#XML_NAME XML_NAME} 类型，对于属性值，其为
     *         {@link com.intellij.psi.xml.XmlTokenType#XML_ATTRIBUTE_VALUE_TOKEN XML_ATTRIBUTE_VALUE_TOKEN}
     *         类型
     * @param resolvedElement
     *         根据 <code>srcElement</code> 所识别出的被引用元素
     */
    @Override
    public @Nullable String generateDoc(PsiElement resolvedElement, @Nullable PsiElement srcElement) {
        if (srcElement == null) {
            return null;
        }

        IElementType elementType = srcElement.getNode().getElementType();
        if (elementType == XML_NAME) {
            return generateDocForXmlName(srcElement);
        } //
        else if (elementType == XML_ATTRIBUTE_VALUE_TOKEN) {
            return generateDocForXmlAttributeValue(srcElement);
        }

        return null;
    }

    /** 为 xml 标签名和属性名生成文档 */
    private String generateDocForXmlName(PsiElement element) {
        PsiElement parent = element.getParent();

        XLangDocumentation doc = null;
        if (parent instanceof XLangTag tag) {
            doc = tag.getTagDocumentation();
        } //
        else if (parent instanceof XLangAttribute attr) {
            String attrName = attr.getName();
            XLangTag tag = attr.getParentTag();

            doc = tag != null ? tag.getAttrDocumentation(attrName) : null;
        }

        return doc != null ? doc.toString() : null;
    }

    /** 为 xml 属性值生成文档 */
    private String generateDocForXmlAttributeValue(PsiElement element) {
        XmlAttribute attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
        if (attr == null) {
            return null;
        }

        String attrName = attr.getName();
        XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);
        XDefTypeDecl attrDefType = tagInfo != null ? tagInfo.getDefAttrType(attrName) : null;
        // TODO 显示类型定义文档
        if (attrDefType == null || attrDefType.getOptions() == null) {
            return null;
        }

        DictBean dictBean = DictProvider.instance().getDict(null, attrDefType.getOptions(), null, null);
        DictOptionBean option = dictBean != null ? dictBean.getOptionByValue(attr.getValue()) : null;
        if (option == null) {
            return null;
        }

        String value = option.getStringValue();
        String label = option.getLabel();

        XLangDocumentation doc = new XLangDocumentation(dictBean);
        doc.setMainTitle(value);

        if (label != null && !Objects.equals(label, value)) {
            doc.setSubTitle(label.startsWith(value + '-') ? label.substring(value.length() + 1) : label);
        }
        doc.setDesc(option.getDescription());

        return doc.toString();
    }

    /** 对于多行文本，行首的 <code>&gt; </code> 将被去除后，再按照 markdown 渲染得到 html 代码 */
    public static String markdown(String text) {
        text = text.replaceAll("(?m)^> ", "");
        text = MarkdownHelper.renderHtml(text);

        return text;
    }
}
