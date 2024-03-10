/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger.config;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import io.nop.idea.plugin.icons.NopIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class XLangRunnerConfigurationType implements ConfigurationType {
    static final String XLANG_RUNNER = "XLang Runner";
    static final String RUNNER_ID = "XLangRunner";

    public static XLangRunnerConfigurationType INSTANCE = new XLangRunnerConfigurationType();

    public XLangRunnerConfigurationType() {

    }

    @Override
    public String getDisplayName() {
        return XLANG_RUNNER;
    }

    @Override
    public String getConfigurationTypeDescription() {
        return XLANG_RUNNER;
    }

    @Override
    public Icon getIcon() {
        return NopIcons.MODULE_TYPE;
    }

    @Override
    @NotNull
    public String getId() {
        return RUNNER_ID;
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        XLangRunnerConfigurationFactory factory = new XLangRunnerConfigurationFactory(this);
        return new ConfigurationFactory[]{factory};
    }
}
