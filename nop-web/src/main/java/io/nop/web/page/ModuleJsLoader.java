/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.TextFile;
import io.nop.web.WebConstants;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.function.Function;

public class ModuleJsLoader implements Function<String, String> {
    private final Cancellable cancellable = new Cancellable();

    @PostConstruct
    public void init() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(WebConstants.FILE_EXT_MJS);

        config.loader(WebConstants.FILE_EXT_MJS, this::loadModuleJs);

        cancellable.append(ResourceComponentManager.instance().registerComponentModelConfig(config));

    }

    @PreDestroy
    public void destroy() {
        cancellable.cancel();
    }

    @Override
    public String apply(String path) {
        if (path.endsWith(".lib")) {
            path += ".js";
        }
        String mjsPath = StringHelper.replaceFileExt(path, WebConstants.FILE_EXT_MJS);
        TextFile file = (TextFile) ResourceComponentManager.instance().loadComponentModel(mjsPath);
        return file.getText();
    }

    private TextFile loadModuleJs(String path) {
        SourceLocation loc = SourceLocation.fromPath(path);
        String mjsPath = StringHelper.replaceFileExt(path, WebConstants.FILE_EXT_MJS);
        IResource resource = VirtualFileSystem.instance().getResource(mjsPath);
        if (resource.exists()) {
            ResourceComponentManager.instance().traceDepends(resource.getPath());
            return new TextFile(loc, ResourceHelper.readText(resource));
        }

        String xjsPath = StringHelper.replaceFileExt(path, WebConstants.FILE_EXT_XJS);
        resource = VirtualFileSystem.instance().getResource(xjsPath);
        if (resource.exists())
            return generateFromXjs(loc, resource);

        String jsPath = StringHelper.replaceFileExt(path, WebConstants.FILE_EXT_JS);
        resource = VirtualFileSystem.instance().getResource(jsPath);
        ResourceComponentManager.instance().traceDepends(resource.getPath());
        return new TextFile(loc, ResourceHelper.readText(resource));
    }

    TextFile generateFromXjs(SourceLocation loc, IResource resource) {
        ResourceComponentManager.instance().traceDepends(resource.getPath());
        String source = ResourceHelper.readText(resource);
        if (StringHelper.isBlank(source)) {
            return SystemJsProvider.emptyFile(loc);
        }

        source = new XGenJsProcessor().process(loc, source);
        // 将动态生成的结果保存到dump目录下
        SystemJsProvider.dumpResource(resource, source);
        return new TextFile(loc, source);
    }
}
