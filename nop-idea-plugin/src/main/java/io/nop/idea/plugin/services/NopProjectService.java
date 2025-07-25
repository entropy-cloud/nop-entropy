/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.services;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import io.nop.api.core.beans.DictBean;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.dict.DictModel;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.idea.plugin.resource.ProjectDictProvider;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.initialize.XLangCoreInitializer;
import io.nop.xlang.xdsl.DslModelParser;

@Service
public final class NopProjectService implements Disposable {
    private final ResourceComponentManager componentManager = new ResourceComponentManager(false);
    private boolean inited;

    private final Cancellable cleanup = new Cancellable();

    private final ResourceLoadingCache<DictModel> dictCache = new ResourceLoadingCache<>("project-dict-cache",
            ProjectDictProvider::loadDictModel, null);

    public NopProjectService() {
    }

    public DictBean getDict(String dictName) {
        DictModel dictModel = dictCache.get(dictName);
        return dictModel == null ? null : dictModel.getDictBean();
    }

    private synchronized void init(Project project) {
        if (inited)
            return;
        inited = true;

        ProjectEnv.withProject(project, () -> {
            XLangCoreInitializer xlang = new XLangCoreInitializer();
            xlang.initialize();
            cleanup.appendOnCancelTask(xlang::destroy);

            registerXlib();
            return null;
        });
    }

    public static NopProjectService get() {
        Project project = ProjectEnv.currentProject();
        if (project == null)
            throw new IllegalStateException("not in project env");

        NopProjectService service = project.getService(NopProjectService.class);
        service.init(project);
        return service;
    }

    public ResourceComponentManager getComponentManager() {
        return componentManager;
    }

    @Override
    public void dispose() {
        cleanup.cancel();
    }

    private void registerXlib() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(XLangConstants.MODEL_TYPE_XLIB);
        config.loader(XLangConstants.FILE_TYPE_XLIB,
                      path -> new DslModelParser(XLangConstants.XDSL_SCHEMA_XLIB).parseFromVirtualPath(path));

        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }
}
