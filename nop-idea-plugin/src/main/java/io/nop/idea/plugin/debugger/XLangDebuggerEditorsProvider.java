/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase;
import io.nop.idea.plugin.lang.XLangFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XLangDebuggerEditorsProvider extends XDebuggerEditorsProviderBase {
    @Override
    protected PsiFile createExpressionCodeFragment(@NotNull Project project, @NotNull String s, @Nullable PsiElement psiElement, boolean b) {
        PsiFile psiFile = new XLangExpressionCodeFragmentImpl(project, "xpl.expr", s);
        return psiFile;
    }

    @Override
    public @NotNull
    FileType getFileType() {
        return XLangFileType.INSTANCE;
    }
}
