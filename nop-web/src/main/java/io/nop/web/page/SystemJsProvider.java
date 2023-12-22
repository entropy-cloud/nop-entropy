/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.TextFile;
import io.nop.web.WebConstants;
import jakarta.annotation.PostConstruct;

import java.util.List;

public class SystemJsProvider extends ResourceWithHistoryProvider {

    @InjectValue("@cfg:nop.js.auto-load-xjs|false")
    boolean autoLoadXjs;

    @PostConstruct
    public void init() {
        if (autoLoadXjs) {
            loadAllXjs();
        }
    }

    public void loadAllXjs() {
        List<IResource> resources = ModuleManager.instance().findModuleResources("/pages", WebConstants.FILE_EXT_XJS);
        resources.forEach(resource -> ResourceComponentManager.instance().loadComponentModel(resource.getStdPath()));
    }

    public String getJs(String path) {
        TextFile file = (TextFile) ResourceComponentManager.instance().loadComponentModel(path);
        return file.getText();
    }

    public String getJsSource(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return ResourceHelper.readText(resource);
    }

    public void saveJsSource(String path, String source) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        withHistorySupport(resource, r -> {
            ResourceHelper.writeText(resource, source);
            return true;
        });
    }
}