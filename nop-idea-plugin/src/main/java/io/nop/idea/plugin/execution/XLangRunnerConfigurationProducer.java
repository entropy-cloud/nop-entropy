/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.execution;

import com.intellij.execution.application.AbstractApplicationConfigurationProducer;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.ConfigurationFactory;
import io.nop.idea.plugin.debugger.config.XLangRunnerConfigurationFactory;
import org.jetbrains.annotations.NotNull;

public class XLangRunnerConfigurationProducer extends AbstractApplicationConfigurationProducer<ApplicationConfiguration> {
    @NotNull
    @Override
    public ConfigurationFactory getConfigurationFactory() {
        return XLangRunnerConfigurationFactory.INSTANCE;
    }
}
