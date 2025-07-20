/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PlatformIcons;
import io.nop.api.core.util.SourceLocation;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xdef.IXDefComment;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdsl.XDslKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对 {@link XLangTag} 的引用识别：指向节点的定义位置
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-17
 */
public class XLangTagReference extends XLangReferenceBase {
    public static final Comparator<String> NAME_COMPARATOR = (a, b) -> {
        int aNsIndex = a.indexOf(':');
        int bNsIndex = b.indexOf(':');

        // 确保无命名空间的属性排在最前面，且 xdef 名字空间排在其他名字空间之前
        if (aNsIndex <= 0 && bNsIndex <= 0) {
            return a.compareTo(b);
        } //
        else if (aNsIndex > 0 && bNsIndex > 0) {
            return !a.startsWith("xdef:") && b.startsWith("xdef:")
                   ? 1
                   : a.startsWith("xdef:") && !b.startsWith("xdef:") //
                     ? -1 : a.compareTo(b);
        }

        return Integer.compare(aNsIndex, bNsIndex);
    };

    public XLangTagReference(XLangTag myElement, TextRange myRangeInElement) {
        super(myElement, myRangeInElement);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        XLangTag tag = (XLangTag) myElement;
        IXDefNode defNode = tag.getSchemaDefNode();

        String path = XmlPsiHelper.getNopVfsPath(defNode);
        if (path == null) {
            return null;
        }

        SourceLocation loc = defNode.getLocation();
        Function<PsiFile, PsiElement> targetResolver = (file) -> XmlPsiHelper.getPsiElementAt(file,
                                                                                              loc,
                                                                                              XLangTag.class);

        return new NopVirtualFile(myElement, path, targetResolver);
    }

    @Override
    public Object @NotNull [] getVariants() {
        XLangTag tag = (XLangTag) myElement;
        XLangTag parentTag = tag.getParentTag();
        if (parentTag == null) {
            return LookupElement.EMPTY_ARRAY;
        }

        String tagNs = tag.getNamespacePrefix();
        boolean usedXDslNs = XDslKeys.DEFAULT.NS.equals(tagNs);

        IXDefNode xdslDefNode = parentTag.getXDslDefNode();
        IXDefNode parentTagDefNode = parentTag.getSchemaDefNode();
        if (usedXDslNs) {
            parentTagDefNode = xdslDefNode;
        }

        if (parentTagDefNode == null) {
            return LookupElement.EMPTY_ARRAY;
        }

        List<IXDefNode> result = new ArrayList<>();
        Set<String> existChildTagNames = XmlPsiHelper.getChildTagNames(parentTag);

        addChildDefNode(result, parentTagDefNode, tagNs, existChildTagNames);
        // xdsl.xdef 的节点对于 DSL 始终可用
        if (!usedXDslNs && xdslDefNode != null) {
            addChildDefNode(result, xdslDefNode, tagNs, existChildTagNames);
        }

        return result.stream()
                     .sorted((a, b) -> NAME_COMPARATOR.compare(a.getTagName(), b.getTagName()))
                     .map((defNode) -> lookupTag(defNode, !tagNs.isEmpty()))
                     .toArray(LookupElement[]::new);
    }

    private static void addChildDefNode(
            List<IXDefNode> list, IXDefNode parentDefNode, String onlyNs, Set<String> excludeNames
    ) {
        for (IXDefNode defNode : parentDefNode.getChildren().values()) {
            String tagName = defNode.getTagName();

            if ((!onlyNs.isEmpty() && !tagName.startsWith(onlyNs + ':')) //
                || defNode.isInternal() //
                || (!defNode.isAllowMultiple() //
                    && excludeNames.contains(tagName) //
                ) //
            ) {
                continue;
            }

            list.add(defNode);
        }
    }

    /** 注意，若当前标签已经包含完整的名字空间，则补全项必须移除其名字空间，否则，补全项的插入位置将会发生偏移 */
    private static LookupElement lookupTag(IXDefNode defNode, boolean trimNs) {
        String label = null;

        IXDefComment comment = defNode.getComment();
        if (comment != null) {
            label = comment.getMainDisplayName();
        }

        String tagName = defNode.getTagName();
        if (trimNs) {
            tagName = tagName.substring(tagName.indexOf(':') + 1);
        }

        return LookupElementBuilder.create(tagName)
                                   // icon 靠左布局
                                   .withIcon(PlatformIcons.XML_TAG_ICON)
                                   // type text 靠后布局
                                   .withTypeText(label)
//                                   // tail text 与 lookup string 紧挨着
//                                   .withTailText(label) //
//                                   // presentable text 将替换 lookup string 作为最终的显示文档
//                                   .withPresentableText(label) //
                                   .withInsertHandler(new XmlTagInsertHandler());
    }
}
