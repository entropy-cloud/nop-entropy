/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.annotator;

import java.util.Set;

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
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangAttributeValue;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.lang.psi.XLangTagMeta;
import io.nop.idea.plugin.lang.psi.XLangText;
import io.nop.idea.plugin.lang.psi.XLangTextToken;
import io.nop.idea.plugin.lang.reference.XLangReference;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.ExceptionHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xpl.utils.XplParseHelper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XLangAnnotator implements Annotator {
    static final Logger LOG = LoggerFactory.getLogger(XLangAnnotator.class);

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
                LOG.debug("nop.validate-xlang-fail", e);

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
        XLangTagMeta tagMeta = tag.getTagMeta();

        if (tagMeta.hasError()) {
            tagErrorAnnotation(holder, tag, tagMeta.getErrorMsg());
        } else {
            checkTagBySchemaDefNode(holder, tag);
        }
    }

    private void checkTagBySchemaDefNode(@NotNull AnnotationHolder holder, @NotNull XLangTag tag) {
        XLangTag parentTag = tag.getParentTag();
        XLangTagMeta tagMeta = tag.getTagMeta();
        XLangTagMeta parentTagMeta = null;

        // 检查是否可包含节点
        if (parentTag != null) {
            parentTagMeta = parentTag.getTagMeta();

            switch (parentTagMeta.checkChildTagAllowed(tagMeta)) {
                // 父标签不允许多个子标签
                case only_at_most_one -> {
                    tagErrorAnnotation(holder,
                                       tag,
                                       "xlang.annotation.tag.only-at-most-one-child-tag",
                                       parentTag.getName());
                    return;
                }
                // 标签自身不可重复
                case can_not_be_multiple -> {
                    tagErrorAnnotation(holder, tag, "xlang.annotation.tag.multiple-tag-not-allowed", tag.getName());
                    return;
                }
                // 标签自身不能被同名标签嵌套
                case can_not_be_nested_by_same_name_tag -> {
                    tagErrorAnnotation(holder,
                                       tag,
                                       "xlang.annotation.tag.nested-by-same-name-tag-not-allowed",
                                       tag.getName());
                    return;
                }
            }
        }

        // 检查节点必需属性是否已设置。Note: 值的有效性由属性值检查过程处理
        Set<String> requiredAttrs = //
                tagMeta.filterRequiredAttrs(parentTagMeta, (attr) -> tag.getAttributeValue(attr) == null, true);
        if (!requiredAttrs.isEmpty()) {
            tagErrorAnnotation(holder,
                               tag,
                               "xlang.annotation.tag.has-unset-required-attrs",
                               tag.getName(),
                               StringHelper.join(requiredAttrs, ","));
            return;
        }

        // 检查节点内容
        XDefTypeDecl xdefValue = tagMeta.getXdefValue();
        TextRange textRange = tag.getValue().getTextRange();
        String bodyText = tag.hasChildTag() ? null : tag.getBodyText();
        boolean blankBodyText = StringHelper.isBlank(bodyText);

        if (xdefValue == null) {
            if (!blankBodyText) {
                errorAnnotation(holder, textRange, "xlang.annotation.tag.value-not-allowed", tag.getName());
            }
            return;
        }

        if (xdefValue.isMandatory() && blankBodyText) {
            tagErrorAnnotation(holder, tag, "xlang.annotation.tag.mandatory-body", tag.getName());
            return;
        }

        if (!blankBodyText) {
            SourceLocation loc = XmlPsiHelper.getValueLocation(tag);

            String stdDomain = xdefValue.getStdDomain();
            if (tagMeta.isXlibSourceNode()) {
                stdDomain = "xpl";
            }

            checkStdDomain(holder, textRange, stdDomain, loc, "body", bodyText);
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

        IXDefAttribute defAttr = attr.getDefAttr();
        if (defAttr == null) {
            errorAnnotation(holder,
                            attrNameElement.getTextRange(),
                            "xlang.annotation.attr.not-defined",
                            attr.getName());
        } //
        else if (defAttr instanceof XLangAttribute.XDefAttributeWithError error) {
            _errorAnnotation(holder, attrNameElement.getTextRange(), error.getErrorMsg());
        }
    }

    private void checkAttrValue(@NotNull AnnotationHolder holder, @NotNull XLangAttributeValue attrValue) {
        XLangAttribute attr = attrValue.getParentAttr();
        IXDefAttribute defAttr = attr != null ? attr.getDefAttr() : null;
        if (XLangAttribute.isNullOrErrorDefAttr(defAttr)) {
            return;
        }

        String attrName = attr.getName();
        String attrValueText = attrValue.getValue();
        TextRange attrValueRange = attrValue.getTextRange(); // 包含引号

        XDefTypeDecl defAttrType = defAttr.getType();
        if (StringHelper.isEmpty(attrValueText)) {
            if (defAttrType.isMandatory()) {
                errorAnnotation(holder, attrValue.getTextRange(), "xlang.annotation.attr.mandatory-value", attrName);
            }
            return;
        }

        // Note: dict/enum 的有效值检查由 PsiReference 处理
        SourceLocation loc = XmlPsiHelper.getLocation(attrValue);
        checkStdDomain(holder, attrValueRange, defAttrType.getStdDomain(), loc, attrName, attrValueText);
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
        if (XplParseHelper.hasExpr(propValue)) {
            return;
        }

        try {
            domainHandler.validate(loc, propName, propValue, IValidationErrorCollector.THROW_ERROR);
        } catch (Exception e) {
            errorAnnotation(holder, textRange, e);
        }
    }

    private void tagErrorAnnotation(AnnotationHolder holder, XmlTag tag, String msgKey, Object... msgParams) {
        String msg = NopPluginBundle.message(msgKey, msgParams);

        tagErrorAnnotation(holder, tag, msg);
    }

    private void tagErrorAnnotation(AnnotationHolder holder, XmlTag tag, String msg) {
        XmlToken[] tokens = new XmlToken[] {
                XmlTagUtil.getStartTagNameElement(tag), XmlTagUtil.getEndTagNameElement(tag)
        };
        for (XmlToken token : tokens) {
            if (token != null) {
                _errorAnnotation(holder, token.getTextRange(), msg);
            }
        }
    }

    private void errorAnnotation(AnnotationHolder holder, TextRange textRange, String msgKey, Object... msgParams) {
        String msg = NopPluginBundle.message(msgKey, msgParams);

        _errorAnnotation(holder, textRange, msg);
    }

    private void errorAnnotation(AnnotationHolder holder, TextRange textRange, Exception e) {
        String msg = ExceptionHelper.getExceptionMessage(e);

        _errorAnnotation(holder, textRange, msg);
    }

    private void _errorAnnotation(AnnotationHolder holder, TextRange textRange, String msg) {
        holder.newAnnotation(HighlightSeverity.ERROR, msg)
              .range(textRange)
              .highlightType(ProblemHighlightType.ERROR)
              .create();
    }
}
