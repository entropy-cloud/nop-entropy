/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.template;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.CreateTemplateInPackageAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiDirectory;
import io.nop.idea.plugin.lang.XLangFileType;
import io.nop.idea.plugin.messages.NopPluginBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

/**
 * 配置 XLang 文件创建窗口
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-08-03
 */
public class CreateXLangFileFromTemplateAction extends CreateFileFromTemplateAction {
    // Note: 模板名不包含后缀 .xdef.ft
    public static final String TEMPLATE_XDEF_NAME = "New_XLang_XDef";
    public static final String TEMPLATE_XDSL_NAME = "New_XLang_XDSL";

    @Override
    protected void buildDialog(
            @NotNull Project project, @NotNull PsiDirectory directory,
            @NotNull CreateFileFromTemplateDialog.Builder builder
    ) {
        builder.setTitle(NopPluginBundle.message("action.NopEntropy.NewXLang.title"));

        builder.addKind("XLang xdef", XLangFileType.INSTANCE.getIcon(), TEMPLATE_XDEF_NAME);
        builder.addKind("XLang DSL", XLangFileType.INSTANCE.getIcon(), TEMPLATE_XDSL_NAME);
    }

    @Override
    protected @NlsContexts.Command String getActionName(
            PsiDirectory directory, @NonNls @NotNull String newName, @NonNls String templateName
    ) {
        return NopPluginBundle.message("action.NopEntropy.NewXLang.text");
    }

    @Override
    protected boolean isAvailable(final DataContext dataContext) {
        return CreateTemplateInPackageAction.isAvailable(dataContext, JavaModuleSourceRootTypes.RESOURCES, (d) -> true);
    }

    @Override
    protected @NotNull PsiDirectory adjustDirectory(@NotNull PsiDirectory directory) {
        return CreateTemplateInPackageAction.adjustDirectory(directory, JavaModuleSourceRootTypes.RESOURCES);
    }
}
