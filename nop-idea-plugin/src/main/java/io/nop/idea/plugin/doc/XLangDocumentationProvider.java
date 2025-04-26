/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.doc;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
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

    String doGenerate(PsiElement element, PsiElement originalElement) {
        PsiElement elm = originalElement;
        if (XmlPsiHelper.isElementType(elm, XmlTokenType.XML_NAME)) {
            PsiElement parent = elm.getParent();

            if (parent instanceof XmlTag) {
                XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(parent);
                if (tagInfo != null && tagInfo.getDefNode() != null) {
                    IXDefComment comment = tagInfo.getDefNode().getComment();
                    String desc = null;
                    if (comment != null) {
                        desc = comment.getMainDescription();
                        if (desc == null)
                            desc = comment.getMainDisplayName();
                    }
                    return doc(desc, tagInfo.getDefNode());
                }
            } else if (parent instanceof XmlAttribute) {
                XmlAttribute attr = (XmlAttribute) parent;
                XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(parent);
                if (tagInfo != null && tagInfo.getDefNode() != null) {
                    IXDefComment comment = tagInfo.getDefNode().getComment();
                    String desc = null;
                    if (comment != null) {
                        desc = comment.getSubDescription(attr.getName());
                        if (desc == null)
                            desc = comment.getSubDisplayName(attr.getName());
                    }
                    return doc(desc, tagInfo.getDefNode().getAttribute(attr.getName()));
                }
            }
        } else if (XmlPsiHelper.isElementType(elm, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN)) {
            XmlAttribute attr = PsiTreeUtil.getParentOfType(elm, XmlAttribute.class);
            if (attr != null) {
                XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);
                if (tagInfo != null && tagInfo.getDefNode() != null) {
                    XDefTypeDecl defType = tagInfo.getDefNode().getAttrType(attr.getName());
                    if (defType != null) {
                        if (defType.getOptions() != null) {
                            DictBean dictBean = DictProvider.instance().getDict(null, defType.getOptions(), null,null);
                            if (dictBean != null) {
                                DictOptionBean option = dictBean.getOptionByValue(attr.getValue());
                                if (option != null) {
                                    if (option.getDescription() != null)
                                        return doc(option.getDescription());
                                    if (!Objects.equals(option.getLabel(), option.getValue()))
                                        return doc(option.getLabel());
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    String doc(String text) {
        if (!StringHelper.isEmpty(text))
            return "doc: " + StringHelper.escapeXml(text);
        return null;
    }

    String doc(String desc, IXDefNode defNode) {
        if (defNode == null)
            return doc(desc);

        XDefTypeDecl type = defNode.getXdefValue();
        if (type != null) {
            return doc("stdDomain=" + type.getStdDomain() + (StringHelper.isBlank(desc) ? "" : "。" + desc));
        }
        return doc(desc);
    }

    String doc(String desc, IXDefAttribute attr) {
        if (attr == null)
            return doc(desc);

        XDefTypeDecl type = attr.getType();
        return doc("stdDomain=" + type.getStdDomain() + (StringHelper.isBlank(desc) ? "" : "。" + desc));
    }

    /**
     * Creates the formatted documentation using {@link DocumentationMarkup}. See the Java doc of
     * {@link com.intellij.lang.documentation.DocumentationProvider#generateDoc(PsiElement, PsiElement)} for more
     * information about building the layout.
     */
    private String renderFullDoc(String key, String value, String file, String docComment) {
        StringBuilder sb = new StringBuilder();
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append("Simple Property");
        sb.append(DocumentationMarkup.DEFINITION_END);
        sb.append(DocumentationMarkup.CONTENT_START);
        sb.append(value);
        sb.append(DocumentationMarkup.CONTENT_END);
        sb.append(DocumentationMarkup.SECTIONS_START);
        addKeyValueSection("Key:", key, sb);
        addKeyValueSection("Value:", value, sb);
        addKeyValueSection("File:", file, sb);
        addKeyValueSection("Comment:", docComment, sb);
        sb.append(DocumentationMarkup.SECTIONS_END);
        return sb.toString();
    }

    /**
     * Creates a key/value row for the rendered documentation.
     */
    private void addKeyValueSection(String key, String value, StringBuilder sb) {
        sb.append(DocumentationMarkup.SECTION_HEADER_START);
        sb.append(key);
        sb.append(DocumentationMarkup.SECTION_SEPARATOR);
        sb.append("<p>");
        sb.append(value);
        sb.append(DocumentationMarkup.SECTION_END);
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
}
