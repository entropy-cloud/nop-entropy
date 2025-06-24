/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.annotation;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.xml.util.XmlTagUtil;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictProvider;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.reference.XLangVfsFileReference;
import io.nop.idea.plugin.reference.XLangElementReference;
import io.nop.idea.plugin.reference.XLangNotFoundReference;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.utils.XmlTagInfo;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import org.jetbrains.annotations.NotNull;

public class XLangAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        ProjectEnv.withProject(element.getProject(), () -> {
            try {
                doAnnotate(element, holder);
            } catch (Exception e) {
                holder.newAnnotation(HighlightSeverity.WARNING,
                                     e.getMessage() != null ? e.getMessage() : e.getClass().getName())
                      .highlightType(ProblemHighlightType.WARNING)
                      .create();
            }
            return null;
        });
    }

    void doAnnotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        for (PsiReference reference : element.getReferences()) {
            if (reference instanceof XLangVfsFileReference //
                || reference instanceof XLangElementReference //
            ) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                      .range(reference.getAbsoluteRange())
                      .textAttributes(DefaultLanguageHighlighterColors.CLASS_REFERENCE)
                      .create();
            } else if (reference instanceof XLangNotFoundReference ref) {
                holder.newAnnotation(HighlightSeverity.ERROR, ref.getMessage())
                      .range(ref.getAbsoluteRange())
                      .highlightType(ProblemHighlightType.ERROR)
                      .create();
            }
        }

        if (element instanceof XmlTag) {
            XmlTag tag = (XmlTag) element;

            XmlTagInfo tagInfo = getTagInfo(tag);
            if (tagInfo == null)
                return;

            // 父节点就不合法，则本节点无需处理
            if (tagInfo.getParentDefNode() == null)
                return;

            checkTag(tagInfo, holder);
        } else if (element instanceof XmlAttribute) {
            XmlTagInfo tagInfo = getTagInfo(element);
            if (tagInfo == null)
                return;
            XmlAttribute attr = (XmlAttribute) element;
            checkAttr(holder, attr, tagInfo);
        } else if (element instanceof XmlAttributeValue) {
            XmlTagInfo tagInfo = getTagInfo(element);
            if (tagInfo == null || tagInfo.getDefNode() == null)
                return;
            String attrName = XmlPsiHelper.getAttrName((XmlAttributeValue) element);
            if (!StringHelper.isBlank(attrName)) {
                XDefTypeDecl attrType = getAttrType(attrName, tagInfo);
                if (attrType != null) {
                    checkAttrValue(holder, attrType, (XmlAttributeValue) element);
                }
            }
        }
    }

    private XmlTagInfo getTagInfo(PsiElement element) {
        return XDefPsiHelper.getTagInfo(element);
    }

    private boolean checkTag(XmlTagInfo tagInfo, AnnotationHolder holder) {
        XmlTag tag = tagInfo.getTag();
        String tagNameStr = tag.getName();

        IXDefNode defNode = tagInfo.getDefNode();
        if (defNode == null) {
            if (tagInfo.isCustom() || tagInfo.isAllowedUnknownName(tagNameStr)) {
                return false;
            }

            XmlToken startTagName = XmlTagUtil.getStartTagNameElement(tag);
            holder.newAnnotation(HighlightSeverity.ERROR, NopPluginBundle.message("xlang.annotation.invalid.tag", tag.getName()))
                    .range(startTagName)
                    .highlightType(ProblemHighlightType.ERROR)
                    .create();

            XmlToken endTagName = XmlTagUtil.getEndTagNameElement(tag);
            if (endTagName != null && endTagName.getText().equals(tagNameStr)) {
                holder.newAnnotation(HighlightSeverity.ERROR, NopPluginBundle.message("xlang.annotation.invalid.tag", tag.getName()))
                        .range(endTagName)
                        .highlightType(ProblemHighlightType.ERROR)
                        .create();
            }
        } else {
            for (XmlAttribute attr : tag.getAttributes()) {
                checkAttr(holder, attr, tagInfo);
            }

            return checkValue(holder, tag, defNode);
        }

        return true;
    }

    private void checkAttr(AnnotationHolder holder, @NotNull XmlAttribute attr,
                           XmlTagInfo tagInfo) {
        String attrName = attr.getName();
        if (StringHelper.isBlank(attrName))
            return;

        XDefTypeDecl attrType = getAttrType(attrName, tagInfo);

        if (attrType == null) {
            if (tagInfo.isAllowedUnknownName(attrName))
                return;

            if (attrName.equals("xmlns") || attrName.startsWith("xmlns:"))
                return;

            holder.newAnnotation(HighlightSeverity.ERROR, NopPluginBundle.message("xlang.annotation.invalid.attr.name", attr.getName()))
                    .range(attr.getNameElement())
                    .highlightType(ProblemHighlightType.ERROR)
                    .create();
        } else {
            if (attr.getValueElement() != null)
                checkAttrValue(holder, attrType, attr.getValueElement());
        }
    }

    private XDefTypeDecl getAttrType(String attrName, XmlTagInfo tagInfo) {
        IXDefNode defNode = tagInfo.getDefNode();

        XDefTypeDecl attrType = null;
        if (attrName.startsWith("xdsl:")) {
            attrName = "x:" + attrName.substring("xdsl:".length());
        }
        IXDefAttribute defAttr = null;
        // 识别系统保留名字空间
        if (attrName.startsWith("x:")) {
            if (tagInfo.getXDslDefNode() != null) {
                defAttr = tagInfo.getXDslDefNode().getAttribute(attrName);
                if (defAttr != null)
                    attrType = defAttr.getType();
            }
        } else if (attrName.startsWith("xpl:")) {
            if (defNode != null) {
                defAttr = defNode.getAttribute(attrName);
                if (defAttr != null)
                    attrType = defAttr.getType();
            }
        } else {
            if (defNode != null) {
                attrType = defNode.getAttrType(attrName);
            }
        }
        return attrType;
    }

    private void checkAttrValue(AnnotationHolder holder, XDefTypeDecl attrType, XmlAttributeValue element) {
        XmlAttribute attr = (XmlAttribute) element.getParent();
        String value = attr.getValue();
        if (StringHelper.isEmpty(value)) {
            if (attrType.isMandatory()) {
                holder.newAnnotation(HighlightSeverity.ERROR, NopPluginBundle.message("xlang.annotation.attr.not-allow-empty", attr.getName()))
                        .range(element)
                        .highlightType(ProblemHighlightType.ERROR)
                        .create();
            }
            return;
        }

        value = StringHelper.unescapeXml(value);

        if (attrType.getStdDomain().equals(XDefConstants.STD_DOMAIN_ENUM)) {
            String dictName = attrType.getOptions();
            if (dictName != null) {
                DictBean dict = DictProvider.instance().getDict(null, dictName, null,null);
                if (dict != null) {
                    if (dict.getOptionByValue(value) == null) {
                        String desc = "value not in " + dict.getValues();
                        holder.newAnnotation(HighlightSeverity.ERROR, desc)
                                .range(element)
                                .highlightType(ProblemHighlightType.ERROR)
                                .create();
                    }
                }
            }
        } else {
            IStdDomainHandler domainHandler = StdDomainRegistry.instance().getStdDomainHandler(attrType.getStdDomain());
            if (domainHandler != null) {
                try {
                    domainHandler.validate(XmlPsiHelper.getLocation(element), attr.getName(), value, IValidationErrorCollector.THROW_ERROR);
                } catch (Exception e) {
                    String desc = ErrorMessageManager.instance().buildErrorMessage(AppConfig.defaultLocale(), e,
                            false, false, false).getDescription();
                    if (desc == null)
                        desc = e.getMessage();
                    holder.newAnnotation(HighlightSeverity.ERROR, "err:" + desc)
                            .range(element)
                            .highlightType(ProblemHighlightType.ERROR)
                            .create();
                }
            }
        }
    }

    private boolean checkValue(AnnotationHolder holder, XmlTag tag, IXDefNode defNode) {

        String tagValue = getTagValue(tag);

        if (defNode.getXdefValue() != null) {
            if (!defNode.hasChild() && !defNode.getXdefValue().isSupportBody(StdDomainRegistry.instance())) {
                // 不允许子节点
                if (XmlPsiHelper.hasChild(tag)) {
                    holder.newAnnotation(HighlightSeverity.ERROR, NopPluginBundle.message("xlang.annotation.tag.not-allow-child", tag.getName()))
                            .range(tag.getValue().getTextRange())
                            .highlightType(ProblemHighlightType.ERROR)
                            .create();
                    return false;
                }
            }
            if (defNode.getXdefValue().isMandatory()) {
                if (StringHelper.isBlank(tagValue)) {
                    holder.newAnnotation(HighlightSeverity.ERROR, NopPluginBundle.message("xlang.annotation.value.not-allow-empty", tag.getName()))
                            .range(getStartTagName(tag))
                            .highlightType(ProblemHighlightType.ERROR)
                            .create();
                }
            }
            if (!StringHelper.isBlank(tagValue)) {
                String domain = defNode.getXdefValue().getStdDomain();
                IStdDomainHandler domainHandler = StdDomainRegistry.instance().getStdDomainHandler(domain);
                if (domainHandler != null) {
                    try {
                        domainHandler.validate(XmlPsiHelper.getValueLocation(tag), "body",tagValue, IValidationErrorCollector.THROW_ERROR);
                    } catch (Exception e) {
                        holder.newAnnotation(HighlightSeverity.ERROR,
                                        "err:" + ErrorMessageManager.instance().buildErrorMessage(AppConfig.defaultLocale(), e,
                                                false, false, false).getDescription())
                                .range(tag.getValue().getTextRange())
                                .highlightType(ProblemHighlightType.ERROR)
                                .create();
                    }
                }
            }
        } else {
            if (!StringHelper.isBlank(tagValue)) {
                holder.newAnnotation(HighlightSeverity.ERROR, NopPluginBundle.message("xlang.annotation.tag.not-allow-value", tag.getName()))
                        .range(tag.getTextRange())
                        .highlightType(ProblemHighlightType.ERROR)
                        .create();
            }
        }

        return true;
    }

    String getTagValue(XmlTag tag) {
        if (XmlPsiHelper.hasChild(tag)) {
            return null;
        }
        return StringHelper.unescapeXml(tag.getValue().getText());
    }

    XmlElement getStartTagName(XmlTag tag) {
        XmlElement element = XmlTagUtil.getStartTagNameElement(tag);
        if (element == null) {
            element = tag;
        }
        return element;
    }
}
