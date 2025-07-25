/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script;

import java.util.Map;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import io.nop.idea.plugin.lang.script.psi.IdentifierNode;
import io.nop.idea.plugin.lang.script.psi.ProgramNode;
import io.nop.idea.plugin.lang.script.reference.IdentifierReference;
import org.jetbrains.annotations.NotNull;

/**
 * XLang Script 的本地变量重命名处理器
 * <p/>
 * 默认的重命名处理器 {@link RenamePsiElementProcessor#DEFAULT}
 * 会调用元素的 {@link PsiNamedElement#setName(String)}
 * 方法完成对重命名节点本身的修改，但不会查找并修改关联方，需要在
 * {@link #prepareRenaming} 中完成对关联方的收集
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-07
 */
public class XLangScriptNodeRenameProcessor extends RenamePsiElementProcessor {

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        // Note: 暂时仅针对标志符节点的名字修改
        return element instanceof IdentifierNode;
    }

    @Override
    public void prepareRenaming(
            @NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames,
            @NotNull SearchScope scope
    ) {
        // TODO thisObj.invoke('doDeleteByQuery', ...) 中的第一个参数名需跟随当前 xlib 的函数标签名联动修改
        ProgramNode root = PsiTreeUtil.getParentOfType(element, ProgramNode.class);
        if (root == null) {
            return;
        }

        // Note: 在 #canProcessElement 限定了仅针对 IdentifierNode，
        // 故而，这里仅检查对 IdentifierNode 的引用的节点
        PsiElement target = element;
        PsiElementVisitor visitor = new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                PsiReference[] refs = element.getReferences();
                for (PsiReference ref : refs) {
                    if (ref instanceof IdentifierReference idRef) {
                        if (idRef.resolve() == target) {
                            allRenames.put(idRef.getIdentifier(), newName);
                        }
                    }
                }

                element.acceptChildren(this);
            }
        };

        root.acceptChildren(visitor);
    }
}
