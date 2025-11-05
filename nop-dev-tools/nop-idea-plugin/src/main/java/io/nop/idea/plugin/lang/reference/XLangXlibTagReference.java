/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import java.util.function.Function;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.lang.xlib.XlibTagMeta;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.LookupElementHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xpl.IXplTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对 xlib 函数标签的引用识别：指向函数的定义位置
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-22
 */
public class XLangXlibTagReference extends XLangReferenceBase {
    /** xlib 标签函数名：不含名字空间 */
    private final String tagName;
    /** xlib 的 vfs 路径 */
    private final String xlibPath;

    public XLangXlibTagReference(XLangTag myElement, TextRange myRangeInElement, String tagName, String xlibPath) {
        super(myElement, myRangeInElement);
        this.tagName = tagName;
        this.xlibPath = xlibPath;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        Function<PsiFile, PsiElement> targetResolver = (file) -> XmlPsiHelper.findFirstElement(file, (element) -> {
            if (element instanceof XLangTag tag) {
                return tag.getName().equals(tagName);
            }
            return false;
        });

        // Note: 仅针对单元测试中的非 vfs 资源内的自引用
        if (xlibPath == null) {
            return targetResolver.apply(myElement.getContainingFile());
        }

        NopVirtualFile target = XlibTagMeta.withLoadedXlib(myElement, xlibPath, (xlib) -> {
            IXplTag xlibTag = xlib.getTag(tagName);
            String targetXlibPath = XmlPsiHelper.getNopVfsPath(xlibTag);

            return targetXlibPath != null //
                   ? new NopVirtualFile(myElement, targetXlibPath, targetResolver) //
                   : null;
        }, null);

        if (target == null || target.hasEmptyChildren()) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.xlib-tag-not-found", tagName, xlibPath);
            setUnresolvedMessage(msg);

            return null;
        }
        return target;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return XlibTagMeta.withLoadedXlib(myElement, xlibPath, (xlib) -> {
            // Note: 标签函数允许递归引用，不需要排除当前标签
            return xlib.getTags().keySet().stream() //
                       .sorted() //
                       .map((name) -> LookupElementHelper.lookupXmlTag(name, null)) //
                       .toArray();
        }, LookupElement.EMPTY_ARRAY);
    }

    /** 对关联的源元素进行更名处理 */
    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        XLangTag element = (XLangTag) myElement;

        return element.setName(newElementName);
    }
}
