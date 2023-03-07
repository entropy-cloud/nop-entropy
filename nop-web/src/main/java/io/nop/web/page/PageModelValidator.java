/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page;

import io.nop.api.core.config.AppConfig;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;

public class PageModelValidator {
    @Inject
    PageProvider pageProvider;

    public void setPageProvider(PageProvider pageProvider) {
        this.pageProvider = pageProvider;
    }

    @PostConstruct
    public void validate() {
        ModuleManager.instance().getEnabledModuleIds().forEach(moduleId -> {
            List<IResource> pageFiles = VirtualFileSystem.instance().findAll("/" + moduleId, "pages/*/*.page.yaml");
            for (IResource resource : pageFiles) {
                pageProvider.getPage(resource.getPath(), AppConfig.defaultLocale());
            }
        });
    }
}
