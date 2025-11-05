/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.testFramework.LightVirtualFile;

public class XLangExpressionCodeFragmentImpl extends PsiPlainTextFileImpl {

    public XLangExpressionCodeFragmentImpl(Project project, String name, String text) {
        super(((PsiManagerEx) PsiManager.getInstance(project)).getFileManager().createFileViewProvider(
                new LightVirtualFile(name, FileTypes.PLAIN_TEXT, text), true));

        ((SingleRootFileViewProvider) getViewProvider()).forceCachedPsi(this);
    }
}
