/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang.annotator;

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension;
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
import com.intellij.psi.xml.XmlToken;
import com.intellij.xml.util.XmlTagUtil;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangAttributeValue;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.lang.psi.XLangText;
import io.nop.idea.plugin.lang.psi.XLangTextToken;
import io.nop.idea.plugin.lang.reference.XLangReference;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import org.jetbrains.annotations.NotNull;

public class XLangAnnotator implements Annotator {

    /**
     * 注意事项：
     * <ul>
     *     <li>
     * 若是 <code>element</code> 检查的结果包含 {@link HighlightSeverity#ERROR}，
     * 则会导致其父节点不再进行检查。详细逻辑见
     * {@link com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass#runVisitors GeneralHighlightingPass#runVisitors}；
     *     </li>
     *     <li>
     * 检查会从 PSI 树的底部向上进行；
     *     </li>
     *     <li>
     * 只能针对指定节点及其子树进行检查，而不能对父节点或兄弟节点做检查，必须确保当前的检查范围在指定节点的
     * {@link PsiElement#getTextRange()} 范围内；
     *     </li>
     *     <li>
     * 只能通过 {@link HighlightRangeExtension#isForceHighlightParents} 针对 XLang 文件扩大检查范围，
     * 避免子节点的检查错误中断对父节点的检查；
     *     </li>
     * </ul>
     */
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlElement)) {
            return;
        }

        ProjectEnv.withProject(element.getProject(), () -> {
            try {
                doAnnotate(holder, element);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();

                holder.newAnnotation(HighlightSeverity.WARNING, msg)
                      .highlightType(ProblemHighlightType.WARNING)
                      .create();
            }
            return null;
        });
    }

    private void doAnnotate(@NotNull AnnotationHolder holder, @NotNull PsiElement element) {
        checkReferences(holder, element);

        if (element instanceof XLangTag tag) {
            checkTag(holder, tag);
        } //
        else if (element instanceof XLangAttribute attr) {
            checkAttr(holder, attr);
        } //
        else if (element instanceof XLangAttributeValue attrValue) {
            checkAttrValue(holder, attrValue);
        }
        // Note: Annotator 不会触发对 XLangTextToken 等 AST 叶子节点的检查，
        // 需通过其父节点（XLangText 等）触发对 XLangTextToken 的检查
        else if (element instanceof XLangText text) {
            for (XLangTextToken token : text.getTextTokens()) {
                doAnnotate(holder, token);
            }
        }
    }

    private void checkReferences(@NotNull AnnotationHolder holder, @NotNull PsiElement element) {
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
                _errorAnnotation(holder, textRange, msg);
            }
        }
    }

    private void checkTag(@NotNull AnnotationHolder holder, @NotNull XLangTag tag) {
        if (tag.getSchemaDefNode() != null) {
            checkTagValue(holder, tag);
            return;
        }

        XLangTag parentTag = tag.getParentTag();
        if ((parentTag != null && parentTag.isXdefValueSupportBody()) //
            || tag.isAllowedUnknownTag() //
        ) {
            return;
        }

        XmlToken startTagName = XmlTagUtil.getStartTagNameElement(tag);
        if (startTagName != null) {
            errorAnnotation(holder,
                            startTagName.getTextRange(),
                            "xlang.annotation.tag.not-defined",
                            startTagName.getText());
        }

        XmlToken endTagName = XmlTagUtil.getEndTagNameElement(tag);
        if (endTagName != null) {
            errorAnnotation(holder,
                            endTagName.getTextRange(),
                            "xlang.annotation.tag.not-defined",
                            endTagName.getText());
        }
    }

    private void checkTagValue(@NotNull AnnotationHolder holder, @NotNull XLangTag tag) {
        XDefTypeDecl xdefValue = tag.getDefNodeXdefValue();
        TextRange textRange = tag.getValue().getTextRange();
        String bodyText = tag.hasChildTag() ? null : tag.getBodyText();
        boolean blankBodyText = StringHelper.isBlank(bodyText);

        if (xdefValue == null) {
            if (!blankBodyText) {
                errorAnnotation(holder, textRange, "xlang.annotation.tag.value-not-allowed", tag.getName());
            }
            return;
        }

        if (!tag.isAllowedChildTag() && tag.hasChildTag()) {
            errorAnnotation(holder, textRange, "xlang.annotation.tag.child-not-allowed", tag.getName());
            return;
        }

        if (xdefValue.isMandatory() && blankBodyText) {
            errorAnnotation(holder,
                            getStartTagName(tag).getTextRange(),
                            "xlang.annotation.tag.body-required",
                            tag.getName());
            return;
        }

        if (!blankBodyText) {
            SourceLocation loc = XmlPsiHelper.getValueLocation(tag);

            checkStdDomain(holder, textRange, xdefValue.getStdDomain(), loc, "body", bodyText);
        }
    }

    private void checkAttr(@NotNull AnnotationHolder holder, @NotNull XLangAttribute attr) {
        XmlElement attrNameElement = attr.getNameElement();
        if (attrNameElement == null //
            || "xmlns".equals(attrNameElement.getText()) //
            || "xmlns".equals(attr.getNamespacePrefix()) //
        ) {
            return;
        }

        if (attr.getDefAttr() == null) {
            errorAnnotation(holder,
                            attrNameElement.getTextRange(),
                            "xlang.annotation.attr.not-defined",
                            attr.getName());
        }
    }

    private void checkAttrValue(@NotNull AnnotationHolder holder, @NotNull XLangAttributeValue attrValue) {
        XLangAttribute attr = attrValue.getParentAttr();
        IXDefAttribute attrDef = attr != null ? attr.getDefAttr() : null;
        if (attrDef == null) {
            return;
        }

        String attrName = attr.getName();
        String attrValueText = attrValue.getValue();
        TextRange attrValueTextRange = attrValue.getValueTextRange();

        XDefTypeDecl attrType = attrDef.getType();
        if (StringHelper.isEmpty(attrValueText)) {
            if (attrType.isMandatory()) {
                errorAnnotation(holder, attrValue.getTextRange(), "xlang.annotation.attr.value-required", attrName);
            }
            return;
        }

        // Note: dict/enum 的有效值检查由 PsiReference 处理
        SourceLocation loc = XmlPsiHelper.getLocation(attrValue);
        checkStdDomain(holder, attrValueTextRange, attrType.getStdDomain(), loc, attrName, attrValueText);
    }

    private void checkStdDomain(
            AnnotationHolder holder, TextRange textRange, //
            String stdDomain, SourceLocation loc, String propName, String propValue
    ) {
        IStdDomainHandler domainHandler = StdDomainRegistry.instance().getStdDomainHandler(stdDomain);
        if (domainHandler == null) {
            return;
        }

        propValue = StringHelper.unescapeXml(propValue);

        try {
            domainHandler.validate(loc, propName, propValue, IValidationErrorCollector.THROW_ERROR);
        } catch (Exception e) {
            errorAnnotation(holder, textRange, e);
        }
    }

    private XmlElement getStartTagName(XmlTag tag) {
        XmlElement element = XmlTagUtil.getStartTagNameElement(tag);

        return element != null ? element : tag;
    }

    private void errorAnnotation(AnnotationHolder holder, TextRange textRange, String msgKey, Object... msgParams) {
        String msg = NopPluginBundle.message(msgKey, msgParams);

        _errorAnnotation(holder, textRange, msg);
    }

    private void errorAnnotation(AnnotationHolder holder, TextRange textRange, Exception e) {
        String msg = ErrorMessageManager.instance()
                                        .buildErrorMessage(AppConfig.defaultLocale(), e, false, false, false)
                                        .getDescription();
        if (msg == null) {
            msg = e.getMessage();
        }

        _errorAnnotation(holder, textRange, msg);
    }

    private void _errorAnnotation(AnnotationHolder holder, TextRange textRange, String msg) {
        holder.newAnnotation(HighlightSeverity.ERROR, msg)
              .range(textRange)
              .highlightType(ProblemHighlightType.ERROR)
              .create();
    }
}
