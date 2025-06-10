/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.doc;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictModel;
import io.nop.core.dict.DictModelParser;
import io.nop.core.dict.DictProvider;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.MarkdownHelper;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.utils.XmlTagInfo;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefComment;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefTypeDecl;
import org.jetbrains.annotations.NotNull;

import jakarta.annotation.Nullable;
import java.util.Objects;

public class XLangDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable
    String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return ProjectEnv.withProject(element.getProject(), () -> {
            return doGenerate(element, originalElement);
        });
    }

    String doGenerate(PsiElement element, PsiElement elm) {
        if (XmlPsiHelper.isElementType(elm, XmlTokenType.XML_NAME)) {
            PsiElement parent = elm.getParent();

            XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(parent);
            if (tagInfo == null || tagInfo.getDefNode() == null) {
                return null;
            }

            if (parent instanceof XmlTag) {
                DocInfo doc = new DocInfo(tagInfo.getDefNode());

                IXDefComment comment = tagInfo.getDefNode().getComment();
                if (comment != null) {
                    doc.setTitle(comment.getMainDisplayName());
                    doc.setDesc(comment.getMainDescription());
                }
                return doc.toString();
            } else if (parent instanceof XmlAttribute) {
                XmlAttribute attr = (XmlAttribute) parent;
                String attrName = attr.getName();
                DocInfo doc = new DocInfo(tagInfo.getDefNode().getAttribute(attrName));

                IXDefComment comment = tagInfo.getDefNode().getComment();
                if (comment != null) {
                    doc.setTitle(comment.getSubDisplayName(attrName));
                    doc.setDesc(comment.getSubDescription(attrName));
                }
                return doc.toString();
            }
        } else if (XmlPsiHelper.isElementType(elm, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN)) {
            XmlAttribute attr = PsiTreeUtil.getParentOfType(elm, XmlAttribute.class);
            if (attr == null) {
                return null;
            }

            XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);
            if (tagInfo == null || tagInfo.getDefNode() == null) {
                return null;
            }

            XDefTypeDecl defType = tagInfo.getDefNode().getAttrType(attr.getName());
            if (defType == null || defType.getOptions() == null) {
                return null;
            }

            DictBean dictBean = DictProvider.instance().getDict(null, defType.getOptions(), null, null);
            DictOptionBean option = dictBean != null ? dictBean.getOptionByValue(attr.getValue()) : null;
            if (option == null) {
                return null;
            }

            DocInfo doc = new DocInfo();
            if (!Objects.equals(option.getLabel(), option.getValue())) {
                doc.setTitle(option.getLabel());
            }
            doc.setDesc(option.getDescription());

            return doc.toString();
        }

        return null;
    }

    /**
     * Provides documentation when a Simple Language element is hovered with the mouse.
     */
    @Override
    public @Nullable
    String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        return generateDoc(element, originalElement);
    }

    /**
     * ctrl + hover时显示的文档信息
     * <p>
     * Provides the information in which file the Simple language key/value is defined.
     */
    @Override
    public @Nullable
    String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
//        if (element instanceof SimpleProperty) {
//            final String key = ((SimpleProperty) element).getKey();
//            final String file = SymbolPresentationUtil.getFilePathPresentation(element.getContainingFile());
//            return "\"" + key + "\" in " + file;
//        }
        return null;
    }

    /** 对于多行文本，行首的 <code>&gt; </code> 将被去除后，再按照 markdown 渲染得到 html 代码 */
    public static String markdown(String text) {
        text = text.replaceAll("(?m)^> ","");
        text = MarkdownHelper.renderHtml(text);

        return text;
    }

    static class DocInfo {
        String title;
        String stdDomain;
        String desc;

        DocInfo() {
        }

        DocInfo(IXDefNode defNode) {
            this(defNode.getXdefValue());
        }

        DocInfo(IXDefAttribute attr) {
            this(attr.getType());
        }

        DocInfo(XDefTypeDecl type) {
            this.stdDomain = type != null ? type.getStdDomain() : null;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            if (!StringHelper.isBlank(this.title)) {
                sb.append("<p><b>");
                sb.append(StringHelper.escapeXml(this.title));
                sb.append("</b></p>");
            }
            if (this.stdDomain != null) {
                sb.append("<p>");
                sb.append("stdDomain=").append(StringHelper.escapeXml(this.stdDomain));
                sb.append("</p>");
            }

            if (!StringHelper.isBlank(this.desc)) {
                if (!sb.isEmpty()) {
                    sb.append("<hr/><br/>");
                }

                sb.append(markdown(this.desc));
            }

            return !sb.isEmpty() ? sb.toString() : null;
        }
    }
}
