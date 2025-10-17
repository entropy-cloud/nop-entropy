/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.nop.idea.plugin.lang.reference.XLangReferenceHelper.XLANG_NAME_COMPARATOR;

/**
 * 对字典的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-21
 */
public class XLangStdDomainDictReference extends XLangReferenceBase {
    private final String dictName;

    public XLangStdDomainDictReference(PsiElement myElement, TextRange myRangeInElement, String dictName) {
        super(myElement, myRangeInElement);
        this.dictName = dictName;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        NopVirtualFile target = XLangReferenceHelper.createNopVfsForDict(myElement, dictName, null);

        if (target.hasEmptyChildren()) {
            String path = target.getPath();
            String msg = NopPluginBundle.message("xlang.annotation.reference.dict-yaml-not-found", path);
            setUnresolvedMessage(msg);

            return null;
        }
        return target;
    }

    @Override
    public Object @NotNull [] getVariants() {
        Project project = myElement.getProject();

        return ProjectFileHelper.findAllDictNopVfsPaths(project)
                                .stream()
                                .map(ProjectFileHelper::getDictNameFromVfsPath)
                                .sorted(XLANG_NAME_COMPARATOR)
                                .toArray();
    }
}
