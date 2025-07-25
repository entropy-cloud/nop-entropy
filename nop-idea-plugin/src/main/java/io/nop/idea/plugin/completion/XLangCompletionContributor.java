/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.completion;

import java.util.Objects;
import java.util.Set;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.XmlAttributeInsertHandler;
import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.PlatformIcons;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.dict.DictProvider;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangAttributeValue;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefComment;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdsl.XDslKeys;
import org.jetbrains.annotations.NotNull;

/**
 * Completion相关的文档可以参见{@link CompletionContributor}类的注释
 * 或者 https://www.plugin-dev.com/intellij/custom-language/code-completion/
 *
 * @deprecated 通过 {@link com.intellij.psi.PsiReference#getVariants PsiReference#getVariants} 实现补全
 */
@Deprecated
public class XLangCompletionContributor extends CompletionContributor implements DumbAware {

    public XLangCompletionContributor() {
    }

    // 从weex-intellij-plugin项目的WeexCompletion类拷贝的实现代码
    @Override
    public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
        final int offset = context.getStartOffset();
        final PsiFile file = context.getFile();
        final XmlAttributeValue attributeValue = PsiTreeUtil.findElementOfClassAtOffset(file,
                                                                                        offset,
                                                                                        XmlAttributeValue.class,
                                                                                        true);
        if (attributeValue != null && offset == attributeValue.getTextRange().getStartOffset()) {
            context.setDummyIdentifier("");
        }

        final PsiElement at = file.findElementAt(offset);
        if (at != null
            && at.getNode().getElementType() == XmlTokenType.XML_NAME
            && at.getParent() instanceof XmlAttribute //
        ) {
            context.getOffsetMap()
                   .addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, at.getTextRange().getEndOffset());
        }

        if (at != null && at.getParent() instanceof XmlAttributeValue) {
            final int end = at.getParent().getTextRange().getEndOffset();
            final Document document = context.getEditor().getDocument();
            final int lineEnd = document.getLineEndOffset(document.getLineNumber(offset));

            if (lineEnd < end) {
                context.setReplacementOffset(lineEnd);
            }
        }
    }

    // 情况比较简单，因此没有使用extend来注册CompletionProvider，而是直接实现此方法
    @Override
    public void fillCompletionVariants(
            @NotNull final CompletionParameters parameters, @NotNull CompletionResultSet result
    ) {
        PsiElement element = parameters.getPosition().getParent();
        ASTNode node = element.getNode();
        if (node == null) {
            return;
        }

        // return early when there's not prefix
//        String prefix = result.getPrefixMatcher().getPrefix();
//        if (prefix.isEmpty()) {
//            return;
//        }

        ProjectEnv.withProject(parameters.getEditor().getProject(), () -> {
            doFillCompletion(element, result);
            return null;
        });
    }

    private void doFillCompletion(PsiElement element, @NotNull CompletionResultSet result) {
        IElementType elType = element.getNode().getElementType();

        if (elType == XmlElementType.XML_TAG) {
            XLangTag tag = (XLangTag) element;

            XLangTag parentTag = tag.getParentTag();
            IXDefNode parentTagDefNode = parentTag != null ? parentTag.getSchemaDefNode() : null;
            if (parentTag == null) {
                return;
            }

            Set<String> existChildTagNames = XmlPsiHelper.getChildTagNames(parentTag);
            for (IXDefNode childNode : parentTagDefNode.getChildren().values()) {
                if (childNode.isInternal()) {
                    continue;
                }

                if (childNode.isAllowMultiple() || !existChildTagNames.contains(childNode.getTagName())) {
                    result.addElement(buildTag(childNode.getTagName(), childNode));
                }
            }

            result.stopHere();
        } //
        else if (elType == XmlElementType.XML_ATTRIBUTE) {
            XLangAttribute attr = (XLangAttribute) element;

            XLangTag tag = attr.getParentTag();
            if (tag == null) {
                return;
            }

            String prefix = result.getPrefixMatcher().getPrefix();
            IXDefNode defNode = tag.getSchemaDefNode();
            if (prefix.startsWith(XDslKeys.DEFAULT.X_NS_PREFIX)) {
                defNode = tag.getXDslDefNode();
            }

            if (defNode == null) {
                return;
            }

            for (IXDefAttribute defAttr : defNode.getAttributes().values()) {
                String attrName = defAttr.getName();

                if (attrName.startsWith(prefix) && tag.getAttribute(attrName) == null) {
                    result.addElement(buildAttr(attrName, defNode));
                }
            }

            result.stopHere();
        } //
        else if (elType == XmlElementType.XML_ATTRIBUTE_VALUE) {
            XLangAttributeValue value = (XLangAttributeValue) element;

            XLangAttribute attr = value.getParentAttr();
            XLangTag tag = attr != null ? attr.getParentTag() : null;
            if (tag == null) {
                return;
            }

            String attrName = attr.getName();
            IXDefAttribute defAttr = tag.getSchemaDefNodeAttr(attrName);
            if (defAttr != null) {
                completeAttrValue(result, defAttr);
            }

            result.stopHere();
        }
    }

    private void completeAttrValue(CompletionResultSet result, IXDefAttribute attr) {
        String prefix = result.getPrefixMatcher().getPrefix();

        XDefTypeDecl type = attr.getType();
        String stdDomain = type.getStdDomain();
        if ("boolean".equals(stdDomain)) {
            if ("false".startsWith(prefix)) {
                result.addElement(buildAttrValue("false", null));
            }
            if ("true".startsWith(prefix)) {
                result.addElement(buildAttrValue("true", null));
            }
        } //
        else if (XDefConstants.STD_DOMAIN_ENUM.equals(stdDomain)) {
            String dictName = type.getOptions();
            if (dictName == null) {
                return;
            }

            DictBean dict = DictProvider.instance().getDict(null, dictName, null, null);
            if (dict == null || dict.getOptions() == null) {
                return;
            }

            for (DictOptionBean optionBean : dict.getOptions()) {
                if (optionBean.isInternal()) {
                    continue;
                }
                result.addElement(buildAttrValue(ConvertHelper.toString(optionBean.getValue(), ""),
                                                 optionBean.getLabel()));
            }
        }
    }

    LookupElement buildTag(String tagName, IXDefNode defNode) {
        String label = null;

        IXDefComment comment = defNode.getComment();
        if (comment != null) {
            label = comment.getMainDisplayName();
            //desc = comment.getMainDescription();
        }

        LookupElement e = LookupElementBuilder.create(tagName)
//                .withPresentableText("Presentable text")
//                .withItemTextForeground(JBColor.RED)
//                .bold()
                                              .withIcon(PlatformIcons.VARIABLE_ICON)
                                              .withTailText(label)
                                              //.withTypeText(desc, PlatformIcons.CLASS_ICON, true)
                                              .withInsertHandler(new XmlTagInsertHandler())
                                              .withTypeIconRightAligned(true);
        return e;
    }

    LookupElement buildAttr(String attrName, IXDefNode defNode) {
        String label = null;
        IXDefComment comment = defNode.getComment();

        if (comment != null) {
            label = comment.getSubDisplayName(attrName);
        }
        return LookupElementBuilder.create(attrName)
                                   .withTailText(label)
                                   .withInsertHandler(new XmlAttributeInsertHandler());
    }

    LookupElement buildAttrValue(String value, String label) {
        if (Objects.equals(label, value)) {
            label = null;
        }
        return LookupElementBuilder.create(value).withTailText(label);
    }
}
