/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page;

import io.nop.commons.util.StringHelper;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.TextFile;
import io.nop.web.WebConstants;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.List;

import static io.nop.web.WebConfigs.CFG_WEB_AUTO_LOAD_DYNAMIC_FILE;

public class DynamicWebFileProvider extends ResourceWithHistoryProvider {

    @Inject
    DynamicJsLoader jsLoader;

    @Inject
    DynamicCssLoader cssLoader;

    @PostConstruct
    public void init() {
        if (CFG_WEB_AUTO_LOAD_DYNAMIC_FILE.get()) {
            loadAllXjs();
            loadAllXcss();
        }
    }

    public void loadAllXjs() {
        List<IResource> resources = ModuleManager.instance().findModuleResources("/pages", WebConstants.FILE_EXT_XJS);
        resources.forEach(resource -> jsLoader.loadText(StringHelper.replaceFileExt(resource.getStdPath(), WebConstants.FILE_EXT_JS)));

        resources = ModuleManager.instance().findModuleResources("/js", WebConstants.FILE_EXT_XJS);
        resources.forEach(resource -> jsLoader.loadText(StringHelper.replaceFileExt(resource.getStdPath(), WebConstants.FILE_EXT_JS)));
    }

    public void loadAllXcss() {
        List<IResource> resources = ModuleManager.instance().findModuleResources("/pages", WebConstants.FILE_EXT_XCSS);
        resources.forEach(resource -> cssLoader.loadText(StringHelper.replaceFileExt(resource.getStdPath(), WebConstants.FILE_EXT_CSS)));

        resources = ModuleManager.instance().findModuleResources("/css", WebConstants.FILE_EXT_XCSS);
        resources.forEach(resource -> cssLoader.loadText(StringHelper.replaceFileExt(resource.getStdPath(), WebConstants.FILE_EXT_CSS)));
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