/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.nop.api.core.util.SourceLocation;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.utils.LookupElementHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xdef.IXDefComment;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdsl.XDslKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.nop.idea.plugin.lang.reference.XLangReferenceHelper.XLANG_NAME_COMPARATOR;

/**
 * 对 {@link XLangTag} 的引用识别：指向节点的定义位置
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-17
 */
public class XLangTagReference extends XLangReferenceBase {

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

    /** @return 可替换 {@link #myElement} 的标签名（即，在元模型中其父标签所定义的子标签）列表 */
    @Override
    public Object @NotNull [] getVariants() {
        // TODO 对于 DSL 的根节点，其标签名只能为其 xdef 中的根节点名
        // Note: 在自动补全阶段，DSL 结构很可能是不完整的，只能从 xml 角度做分析
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
                     .sorted((a, b) -> XLANG_NAME_COMPARATOR.compare(a.getTagName(), b.getTagName()))
                     .map((defNode) -> lookupTag(defNode, !tagNs.isEmpty()))
                     .toArray(LookupElement[]::new);
    }

    private static void addChildDefNode(
            List<IXDefNode> list, IXDefNode parentDefNode, String onlyNs, Set<String> excludeNames) {
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

        return LookupElementHelper.lookupXmlTag(tagName, label);
    }
}
