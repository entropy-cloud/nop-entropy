/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.utils;

import com.intellij.formatting.FormatTextRanges;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditorHelper {
    static final Logger LOG = LoggerFactory.getLogger(EditorHelper.class);

    @Nullable
    public static Editor openLocation(Project project, @NotNull String uri, int line) {
        try {
            final VirtualFile file = ProjectFileHelper.getVirtualFile(uri);
            int lineNumber = line > 0 ? line - 1 : 0;
            final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, lineNumber, 0);
            descriptor.navigate(true);

            return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
        } catch (Exception e) {
            LOG.warn("nop.invalid-url", e);
            return null;
        }
    }

    public static void format(@NotNull PsiFile file, @NotNull PsiElement element) {
        CodeStyleSettingsManager manager = CodeStyleSettingsManager.getInstance(element.getProject());
        CodeStyleSettings settings = manager.getMainProjectCodeStyle();
        CodeFormatterFacade codeFormatterFacade = new CodeFormatterFacade(settings, element.getLanguage());
        codeFormatterFacade.processText(file, new FormatTextRanges(element.getTextRange(), true), true);
    }
}
