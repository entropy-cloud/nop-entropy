/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.xml.util.XmlTagUtil;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangAttributeValue;
import io.nop.idea.plugin.lang.reference.XLangReference;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.utils.XmlTagInfo;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import org.jetbrains.annotations.NotNull;

public class XLangAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlElement)) {
            return;
        }

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
        // Note: 在识别引用时，处理的是 XmlText 的 cdata 节点，因此，需要对其子节点做高亮处理
        if (element instanceof XmlText text) {
            for (PsiElement child : text.getChildren()) {
                doAnnotate(child, holder);
            }
            return;
        }

        checkReferences(element, holder);

        if (element instanceof XmlTag) {
            XmlTag tag = (XmlTag) element;

            XmlTagInfo tagInfo = getTagInfo(tag);
            if (tagInfo == null) {
                return;
            }

            // 父节点就不合法，则本节点无需处理
            if (tagInfo.getParentDefNode() == null) {
                return;
            }

            checkTag(tagInfo, holder);
        } //
        else if (element instanceof XLangAttribute attr) {
            checkAttr(holder, attr);
        } //
        else if (element instanceof XLangAttributeValue attrValue) {
            XLangAttribute attr = attrValue.getParentAttr();
            IXDefAttribute attrDef = attr != null ? attr.getDefAttr() : null;
            if (attrDef == null) {
                return;
            }

            XDefTypeDecl attrType = attrDef.getType();
            checkAttrValue(holder, attr, attrType, attrValue);
        }
    }

    private XmlTagInfo getTagInfo(PsiElement element) {
        return XDefPsiHelper.getTagInfo(element);
    }

    private void checkReferences(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        for (PsiReference reference : element.getReferences()) {
            if (!(reference instanceof XLangReference ref)) {
                continue;
            }

            TextRange textRange = ref.getAbsoluteRange();
            PsiElement target = ref.resolve();
            if (target instanceof NopVirtualFile vfs && vfs.forFileChildren()) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                      .range(textRange)
                      .textAttributes(DefaultLanguageHighlighterColors.DOC_COMMENT_TAG_VALUE)
                      .create();
            }

            String msg = ref.getUnresolvedMessage();
            if (target == null && msg != null) {
                holder.newAnnotation(HighlightSeverity.ERROR, msg)
                      .range(textRange)
                      .highlightType(ProblemHighlightType.ERROR)
                      .create();
            }
        }
    }

    private void checkTag(XmlTagInfo tagInfo, AnnotationHolder holder) {
        XmlTag tag = tagInfo.getTag();
        String tagNameStr = tag.getName();

        IXDefNode defNode = tagInfo.getDefNode();
        if (defNode == null) {
            if (tagInfo.isCustom() || tagInfo.isAllowedUnknownName(tagNameStr)) {
                return;
            }

            XmlToken startTagName = XmlTagUtil.getStartTagNameElement(tag);
            holder.newAnnotation(HighlightSeverity.ERROR,
                                 NopPluginBundle.message("xlang.annotation.invalid.tag", tag.getName()))
                  .range(startTagName)
                  .highlightType(ProblemHighlightType.ERROR)
                  .create();

            XmlToken endTagName = XmlTagUtil.getEndTagNameElement(tag);
            if (endTagName != null && endTagName.getText().equals(tagNameStr)) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                                     NopPluginBundle.message("xlang.annotation.invalid.tag", tag.getName()))
                      .range(endTagName)
                      .highlightType(ProblemHighlightType.ERROR)
                      .create();
            }
        } else {
            checkValue(holder, tag, defNode);
        }
    }

    private void checkAttr(AnnotationHolder holder, @NotNull XLangAttribute attr) {
        XmlElement attrNameElement = attr.getNameElement();
        if (attrNameElement == null //
            || "xmlns".equals(attrNameElement.getText()) //
            || "xmlns".equals(attr.getNamespacePrefix()) //
        ) {
            return;
        }

        if (attr.getDefAttr() == null) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                                 NopPluginBundle.message("xlang.annotation.attr.not-defined", attr.getName()))
                  .range(attrNameElement)
                  .highlightType(ProblemHighlightType.ERROR)
                  .create();
        }
    }

    private void checkAttrValue(
            AnnotationHolder holder, XLangAttribute attr, XDefTypeDecl attrType, XLangAttributeValue attrValue
    ) {
        String attrName = attr.getName();
        String attrValueText = attrValue.getValue();
        TextRange attrValueTextRange = attrValue.getValueTextRange();

        if (StringHelper.isEmpty(attrValueText)) {
            if (attrType.isMandatory()) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                                     NopPluginBundle.message("xlang.annotation.attr.value-required", attrName))
                      .range(attrValue.getTextRange())
                      .highlightType(ProblemHighlightType.ERROR)
                      .create();
            }
            return;
        }

        // Note: dict/enum 的有效值检查由 PsiReference 处理
        attrValueText = StringHelper.unescapeXml(attrValueText);

        IStdDomainHandler domainHandler = StdDomainRegistry.instance().getStdDomainHandler(attrType.getStdDomain());
        if (domainHandler == null) {
            return;
        }

        try {
            domainHandler.validate(XmlPsiHelper.getLocation(attrValue),
                                   attrName,
                                   attrValueText,
                                   IValidationErrorCollector.THROW_ERROR);
        } catch (Exception e) {
            String msg = ErrorMessageManager.instance()
                                            .buildErrorMessage(AppConfig.defaultLocale(), e, false, false, false)
                                            .getDescription();
            if (msg == null) {
                msg = e.getMessage();
            }

            holder.newAnnotation(HighlightSeverity.ERROR, msg)
                  .range(attrValueTextRange)
                  .highlightType(ProblemHighlightType.ERROR)
                  .create();
        }
    }

    private void checkValue(AnnotationHolder holder, XmlTag tag, IXDefNode defNode) {

        String tagValue = getTagValue(tag);

        if (defNode.getXdefValue() != null) {
            if (!defNode.hasChild() && !defNode.getXdefValue().isSupportBody(StdDomainRegistry.instance())) {
                // 不允许子节点
                if (XmlPsiHelper.hasChild(tag)) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                                         NopPluginBundle.message("xlang.annotation.tag.not-allow-child", tag.getName()))
                          .range(tag.getValue().getTextRange())
                          .highlightType(ProblemHighlightType.ERROR)
                          .create();
                    return;
                }
            }
            if (defNode.getXdefValue().isMandatory()) {
                if (StringHelper.isBlank(tagValue)) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                                         NopPluginBundle.message("xlang.annotation.value.not-allow-empty",
                                                                 tag.getName()))
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
                        domainHandler.validate(XmlPsiHelper.getValueLocation(tag),
                                               "body",
                                               tagValue,
                                               IValidationErrorCollector.THROW_ERROR);
                    } catch (Exception e) {
                        holder.newAnnotation(HighlightSeverity.ERROR,
                                             "err:" + ErrorMessageManager.instance()
                                                                         .buildErrorMessage(AppConfig.defaultLocale(),
                                                                                            e,
                                                                                            false,
                                                                                            false,
                                                                                            false)
                                                                         .getDescription())
                              .range(tag.getValue().getTextRange())
                              .highlightType(ProblemHighlightType.ERROR)
                              .create();
                    }
                }
            }
        } else {
            if (!StringHelper.isBlank(tagValue)) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                                     NopPluginBundle.message("xlang.annotation.tag.not-allow-value", tag.getName()))
                      .range(tag.getTextRange())
                      .highlightType(ProblemHighlightType.ERROR)
                      .create();
            }
        }
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
