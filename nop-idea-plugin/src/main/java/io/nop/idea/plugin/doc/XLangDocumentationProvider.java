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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictProvider;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.MarkdownHelper;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.utils.XmlTagInfo;
import io.nop.xlang.xdef.IXDefComment;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefSubComment;
import io.nop.xlang.xdef.XDefTypeDecl;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

public class XLangDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return ProjectEnv.withProject(element.getProject(), () -> {
            if (XmlPsiHelper.isElementType(originalElement, XmlTokenType.XML_NAME)) {
                return generateDocForXmlName(originalElement);
            } //
            else if (XmlPsiHelper.isElementType(originalElement, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN)) {
                return generateDocForXmlAttributeValue(originalElement);
            }
            return null;
        });
    }

    /**
     * Provides documentation when a Simple Language element is hovered with the mouse.
     */
    @Override
    public @Nullable String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        return generateDoc(element, originalElement);
    }

    /**
     * ctrl + hover时显示的文档信息
     * <p>
     * Provides the information in which file the Simple language key/value is defined.
     */
    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
//        if (element instanceof SimpleProperty) {
//            final String key = ((SimpleProperty) element).getKey();
//            final String file = SymbolPresentationUtil.getFilePathPresentation(element.getContainingFile());
//            return "\"" + key + "\" in " + file;
//        }
        return null;
    }

    /** 为 xml 标签名和属性名生成文档 */
    private String generateDocForXmlName(PsiElement element) {
        PsiElement parent = element.getParent();

        XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(parent);
        if (tagInfo == null || tagInfo.getDefNode() == null) {
            return null;
        }

        if (parent instanceof XmlTag tag) {
            DocInfo doc = new DocInfo(tagInfo.getDefNode());
            doc.setMainTitle(tag.getName());

            IXDefComment comment = tagInfo.getDefNodeComment();
            if (comment != null) {
                doc.setSubTitle(comment.getMainDisplayName());
                doc.setDesc(comment.getMainDescription());
            }
            return doc.toString();
        } else if (parent instanceof XmlAttribute attr) {
            String attrName = attr.getName();

            DocInfo doc = new DocInfo(tagInfo.getDefAttrType(attrName));
            doc.setMainTitle(attrName);

            IXDefSubComment comment = tagInfo.getDefAttrComment(attrName);
            if (comment != null) {
                doc.setSubTitle(comment.getDisplayName());
                doc.setDesc(comment.getDescription());
            }
            return doc.toString();
        }

        return null;
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

        DocInfo doc = new DocInfo();
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

    static class DocInfo {
        String mainTitle;
        String subTitle;
        String stdDomain;
        String desc;

        DocInfo() {
        }

        DocInfo(IXDefNode defNode) {
            this(defNode.getXdefValue());
        }

        DocInfo(XDefTypeDecl type) {
            if (type != null) {
                this.stdDomain = type.getStdDomain();
                if (type.getOptions() != null) {
                    this.stdDomain += ':' + type.getOptions();
                }
            }
        }

        public void setMainTitle(String mainTitle) {
            this.mainTitle = mainTitle;
        }

        public void setSubTitle(String subTitle) {
            this.subTitle = subTitle;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("<p><b>");
            sb.append(StringHelper.escapeXml(this.mainTitle));
            if (StringHelper.isNotBlank(this.subTitle)) {
                sb.append(" - ").append(StringHelper.escapeXml(this.subTitle));
            }
            sb.append("</b></p>");

            if (this.stdDomain != null) {
                sb.append("<p>");
                sb.append("stdDomain: ");
                sb.append("<b>").append(StringHelper.escapeXml(this.stdDomain)).append("</b>");
                sb.append("</p>");
            }

            if (!StringHelper.isBlank(this.desc)) {
                sb.append("<hr/><br/>");
                sb.append(markdown(this.desc));
            }

            return !sb.isEmpty() ? sb.toString() : null;
        }
    }
}
