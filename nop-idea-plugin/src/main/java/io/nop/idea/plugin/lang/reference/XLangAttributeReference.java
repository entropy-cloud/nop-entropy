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

import com.intellij.codeInsight.completion.XmlAttributeInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PlatformIcons;
import io.nop.api.core.util.SourceLocation;
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.lang.xlib.XlibTagMeta;
import io.nop.idea.plugin.lang.xlib.XlibXDefAttribute;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefComment;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefKeys;
import io.nop.xlang.xdsl.XDslKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对 {@link XLangAttributeReference} 的引用识别：指向属性的定义位置
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-10
 */
public class XLangAttributeReference extends XLangReferenceBase {
    private final IXDefAttribute defAttr;

    public XLangAttributeReference(XLangAttribute myElement, TextRange myRangeInElement, IXDefAttribute defAttr) {
        super(myElement, myRangeInElement);
        this.defAttr = defAttr;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        if (defAttr == null) {
            return null;
        }

        String path = XmlPsiHelper.getNopVfsPath(defAttr);
        if (path == null) {
            return null;
        }

        SourceLocation loc = defAttr.getLocation();
        Function<PsiFile, PsiElement> targetResolver = (file) -> {
            PsiElement target = XmlPsiHelper.getPsiElementAt(file, loc, XLangAttribute.class);

            if (target == null) {
                target = XmlPsiHelper.getPsiElementAt(file, loc, XLangTag.class);

                if (target instanceof XLangTag tag) {
                    // Note: 在交叉定义时，属性定义中的属性名字与当前属性名字是不相同的
                    target = tag.getAttribute(defAttr.getName());
                }
            }
            return target;
        };

        return new NopVirtualFile(myElement, path, targetResolver);
    }

    @Override
    public Object @NotNull [] getVariants() {
        // Note: 在自动补全阶段，DSL 结构很可能是不完整的，只能从 xml 角度做分析
        XLangAttribute attr = (XLangAttribute) myElement;
        XLangTag tag = attr.getParentTag();
        if (tag == null) {
            return LookupElement.EMPTY_ARRAY;
        }

        String attrNs = attr.getNamespacePrefix();
        XDefKeys xdefKeys = tag.getXDefKeys();
        XDslKeys xdslKeys = tag.getXDslKeys();
        // Note: 需支持处理 x/xdef 的名字空间非默认的情况
        boolean usedXDefNs = xdefKeys.NS.equals(attrNs);
        boolean usedXDslNs = xdslKeys.NS.equals(attrNs);

        IXDefNode xdslDefNode = tag.getXDslDefNode();
        IXDefNode tagDefNode = tag.getSchemaDefNode();
        if (usedXDslNs) {
            tagDefNode = xdslDefNode;
        }

        if (tagDefNode == null) {
            return LookupElement.EMPTY_ARRAY;
        }

        List<DefAttrWithLabel> result = new ArrayList<>();
        Set<String> existAttrNames = XmlPsiHelper.getTagAttrNames(tag);

        String stdNs = usedXDefNs ? XDefKeys.DEFAULT.NS //
                                  : usedXDslNs //
                                    ? XDslKeys.DEFAULT.NS : attrNs;
        addDefAttr(result, tagDefNode, stdNs, existAttrNames);
        // xdsl.xdef 的节点属性对于 DSL 始终可用
        if (!usedXDslNs && xdslDefNode != null) {
            addDefAttr(result, xdslDefNode, attrNs, existAttrNames);
        }
        // 引入可能的 xlib 标签函数的参数
        addDefAttr(result, tag.getXlibTagMeta(), existAttrNames);

        return result.stream() //
                     .sorted((a, b) -> XLangReferenceHelper.XLANG_NAME_COMPARATOR.compare(a.name, b.name)) //
                     .map((defAttr) -> {
                         boolean trimNs = !attrNs.isEmpty();

                         String attrName = defAttr.name;
                         if (!trimNs) {
                             attrName = XLangTag.changeNamespace(attrName, XDefKeys.DEFAULT.NS, xdefKeys.NS);
                             attrName = XLangTag.changeNamespace(attrName, XDslKeys.DEFAULT.NS, xdslKeys.NS);
                         }

                         return lookupAttr(attrName, defAttr.label, trimNs);
                     }) //
                     .toArray(LookupElement[]::new);
    }

    private static void addDefAttr(
            List<DefAttrWithLabel> list, IXDefNode defNode, String onlyNs, Set<String> excludeNames
    ) {
        for (IXDefAttribute defAttr : defNode.getAttributes().values()) {
            String attrName = defAttr.getName();

            if ((!onlyNs.isEmpty() && !attrName.startsWith(onlyNs + ':')) //
                || excludeNames.contains(attrName) //
            ) {
                continue;
            }

            String label = null;
            IXDefComment comment = defNode.getComment();
            if (comment != null) {
                label = comment.getSubDisplayName(attrName);
            }

            list.add(new DefAttrWithLabel(attrName, defAttr, label));
        }
    }

    private static void addDefAttr(List<DefAttrWithLabel> list, XlibTagMeta xlibTag, Set<String> excludeNames) {
        if (xlibTag == null) {
            return;
        }

        for (XlibXDefAttribute defAttr : xlibTag.getAttributes().values()) {
            String attrName = defAttr.getName();
            if (excludeNames.contains(attrName)) {
                continue;
            }

            list.add(new DefAttrWithLabel(attrName, defAttr, defAttr.label));
        }
    }

    /** 注意，若当前属性已经包含完整的名字空间，则补全项必须移除其名字空间，否则，补全项的插入位置将会发生偏移 */
    private static LookupElement lookupAttr(String attrName, String label, boolean trimNs) {
        if (trimNs) {
            attrName = attrName.substring(attrName.indexOf(':') + 1);
        }

        return LookupElementBuilder.create(attrName)
                                   // icon 靠左布局
                                   .withIcon(PlatformIcons.PROPERTY_ICON)
                                   // type text 靠后布局
                                   .withTypeText(label)
//                                   // tail text 与 lookup string 紧挨着
//                                   .withTailText(label) //
//                                   // presentable text 将替换 lookup string 作为最终的显示文本
//                                   .withPresentableText(label) //
                                   .withInsertHandler(new XmlAttributeInsertHandler());
    }

    private record DefAttrWithLabel(String name, IXDefAttribute def, String label) {}
}
