/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang;

import java.util.Map;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * XLang 元素重命名的处理器
 * <p/>
 * 默认的重命名处理器 {@link RenamePsiElementProcessor#DEFAULT}
 * 会调用元素的 {@link PsiNamedElement#setName(String)}
 * 方法完成对重命名节点本身的修改，但不会查找并修改关联方，需要在
 * {@link #prepareRenaming} 中完成对关联方的收集
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-23
 */
public class XLangElementRenameProcessor extends RenamePsiElementProcessor {

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        return element instanceof NopVirtualFile;
    }

    @Override
    public void prepareRenaming(
            @NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames,
            @NotNull SearchScope scope
    ) {
        if (element instanceof NopVirtualFile vfs) {
            vfs.prepareRenaming(newName, allRenames);
            return;
        }

//        XLangDocument root = PsiTreeUtil.getParentOfType(element, XLangDocument.class);
//        if (root == null) {
//            return;
//        }
//
//        PsiElement target = element;
//        PsiElementVisitor visitor = new PsiElementVisitor() {
//            @Override
//            public void visitElement(@NotNull PsiElement element) {
//                for (PsiReference ref : element.getReferences()) {
//                    if (ref instanceof XLangXlibTagReference || ref instanceof XLangTagReference) {
//                        PsiElement resolved = ref.resolve();
//
//                        if (resolved == target) {
//                            allRenames.put(ref.getElement(), newName);
//                        }
//                    }
//                }
//
//                element.acceptChildren(this);
//            }
//        };
//
//        root.acceptChildren(visitor);
    }
}
