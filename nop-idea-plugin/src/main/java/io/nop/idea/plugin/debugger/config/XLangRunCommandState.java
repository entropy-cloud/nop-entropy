/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger.config;

import com.intellij.execution.application.ApplicationCommandLineState;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.annotations.NotNull;

public class XLangRunCommandState extends ApplicationCommandLineState<XLangRunConfiguration> {
    public XLangRunCommandState(@NotNull XLangRunConfiguration configuration,
                                @NotNull ExecutionEnvironment environment) {
        super(configuration, environment);
    }

    @Override
    protected boolean isProvidedScopeIncluded() {
        return myConfiguration.isProvidedScopeIncluded();
    }
}
