/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.services;

import com.intellij.ide.AppLifecycleListener;
import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.json.JSON;
import io.nop.commons.util.ClassHelper;
import io.nop.core.dict.DictProvider;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.idea.plugin.resource.ProjectDictProvider;
import io.nop.idea.plugin.resource.ProjectResourceComponentManager;
import io.nop.idea.plugin.resource.ProjectVirtualFileSystem;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NopAppListener implements AppLifecycleListener {

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        // Note: 采用默认的加载器（ClassHelper#getDefaultClassLoader），在项目中会无法加载 IXplTagLib 的实现，原因未知
        // TODO 在加载 DSL 时，是否会因为执行 x:gen-extends 等脚本而产生安全风险？
        ClassHelper.registerSafeClassLoader((name) -> getClass().getClassLoader().loadClass(name));

        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_DEBUG, false);

        JSON.registerProvider(JsonTool.instance());
        VirtualFileSystem.registerInstance(new ProjectVirtualFileSystem());
        DictProvider.registerInstance(new ProjectDictProvider());

        ResourceComponentManager.registerInstance(new ProjectResourceComponentManager());
    }

    @Override
    public void appClosing() {
    }
}
