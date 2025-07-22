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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.LookupElementHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;
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

        // 得到标签函数实际定义所在的 xlib
        IXplTagLib xlib = loadXlib();
        IXplTag xlibTag = xlib != null ? xlib.getTag(tagName) : null;
        String targetXlibPath = XmlPsiHelper.getNopVfsPath(xlibTag);

        NopVirtualFile target = targetXlibPath != null
                                ? new NopVirtualFile(myElement, targetXlibPath, targetResolver)
                                : null;
        if (target == null || target.hasEmptyChildren()) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.xlib-tag-not-found", tagName, xlibPath);
            setUnresolvedMessage(msg);

            return null;
        }
        return target;
    }

    @Override
    public Object @NotNull [] getVariants() {
        IXplTagLib xlib = loadXlib();
        if (xlib == null) {
            return LookupElement.EMPTY_ARRAY;
        }

        // Note: 标签函数允许递归引用，不需要排除当前标签
        return xlib.getTags().keySet().stream() //
                   .sorted() //
                   .map((name) -> LookupElementHelper.lookupXmlTag(name, null)) //
                   .toArray();
    }

    private IXplTagLib loadXlib() {
        // xlib 是可扩展的，因此，需要直接加载 xlib 模型以获取准确的标签函数名
        // TODO 在插件内加载 DSL，是否会因为执行 x:gen-extends 等脚本而产生安全风险？
        Project project = myElement.getProject();

        return ProjectEnv.withProject(project, () -> XplLibHelper.loadLib(xlibPath));
    }
}
