/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import io.nop.commons.util.CharSequenceHelper;
import io.nop.idea.plugin.lang.XLangFileType;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XLangBreakpointType extends XLineBreakpointType<XLangBreakpointProperties> {
    public XLangBreakpointType() {
        super("xlang-line", NopPluginBundle.message("xlang.debugger.line.breakpoints.tab.title"));
    }

    @Override
    public @Nullable
    XLangBreakpointProperties createBreakpointProperties(@NotNull VirtualFile virtualFile, int i) {
        return new XLangBreakpointProperties();
    }

    @Override
    public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
        boolean yaml = file.getFileType().getName().equalsIgnoreCase("YAML");
        // 如果返回false，则本行和XML注释无法放置断点，目前是空行不允许增加断点
        return (file.getFileType() instanceof XLangFileType || yaml)
                && canPlaceBreakpointAt(file, line, project);
    }

    static boolean canPlaceBreakpointAt(VirtualFile file, int line, Project project) {
        final Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null)
            return false;

        CharSequence str = ProjectFileHelper.getLine(document, line);
        if (str == null)
            return false;

        str = CharSequenceHelper.trim(str);

        if (CharSequenceHelper.isEmpty(str))
            return false;

        LineChecker canPutAtChecker =
                new LineChecker();
        XDebuggerUtil.getInstance().iterateLine(project, document, line, canPutAtChecker);
        return canPutAtChecker.isAvailable();
    }

    static class LineChecker implements Processor<PsiElement> {
        private boolean available;

        @Override
        public boolean process(@NotNull PsiElement element) {
            if (XmlPsiHelper.isInComment(element))
                return true;
            available = true;
            return true;
        }

        boolean isAvailable() {
            return available;
        }
    }

    @Nullable
    public final XDebuggerEditorsProvider getEditorsProvider(@NotNull XLineBreakpoint breakpoint, @NotNull Project project) {
        return new XLangDebuggerEditorsProvider();//JavaDebuggerEditorsProvider();
    }
}
