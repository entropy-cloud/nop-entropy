/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.debugger.config;

import com.intellij.execution.application.JvmMainMethodRunConfigurationOptions;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XLangRunnerConfigurationFactory extends ConfigurationFactory {
    public static XLangRunnerConfigurationFactory INSTANCE = new XLangRunnerConfigurationFactory(XLangRunnerConfigurationType.INSTANCE);

    public XLangRunnerConfigurationFactory(@NotNull ConfigurationType type) {
        super(type);
    }

    public boolean isEditableInDumbMode() {
        return true;
    }

    public Class<? extends BaseState> getOptionsClass() {
        return JvmMainMethodRunConfigurationOptions.class;
    }

    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new XLangRunConfiguration("", project, this);
    }

    public @NotNull @NonNls String getId() {
        return getType().getDisplayName();
    }

}
