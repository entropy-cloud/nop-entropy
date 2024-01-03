package io.nop.web.page;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceTextLoader;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.TextFile;
import io.nop.web.WebConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.File;

import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_EXISTS;
import static io.nop.web.WebConfigs.CFG_WEB_USE_DYNAMIC_CSS;

public class DynamicCssLoader implements IResourceTextLoader {

    private final Cancellable cancellable = new Cancellable();

    @PostConstruct
    public void init() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(WebConstants.FILE_EXT_CSS);

        config.loader(WebConstants.FILE_EXT_CSS, this::loadDynamicCss);

        cancellable.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    @PreDestroy
    public void destroy() {
        cancellable.cancel();
    }

    @Override
    public String loadText(String path) {
        TextFile file = (TextFile) ResourceComponentManager.instance().loadComponentModel(path);
        return file.getText();
    }

    protected IComponentModel loadDynamicCss(String path) {
        SourceLocation loc = SourceLocation.fromPath(path);

        IResource resource = VirtualFileSystem.instance().getResource(path);
        String xcssPath = StringHelper.replaceFileExt(path, WebConstants.FILE_EXT_XCSS);
        IResource xcssResource = VirtualFileSystem.instance().getResource(xcssPath);

        if (!CFG_WEB_USE_DYNAMIC_CSS.get() || !xcssResource.exists()) {
            if (resource.exists()) {
                ResourceComponentManager.instance().traceDepends(resource.getPath());
                return new TextFile(loc, ResourceHelper.readText(resource));
            }
            throw new NopException(ERR_RESOURCE_NOT_EXISTS).param(ARG_RESOURCE_PATH, path);
        }

        ResourceComponentManager.instance().traceDepends(xcssResource.getPath());

        String source = ResourceHelper.readText(xcssResource);
        if (!StringHelper.isBlank(source)) {
            source = new WebDynamicFileProcessor().process(loc, source);
        }

        // 将动态生成的结果保存到dump目录下
        ResourceHelper.dumpResource(resource, source);

        // 如果xjs文件在文件目录中，则直接生成一个对应的js文件到同样的目录下
        File file = xcssResource.toFile();
        if (file != null) {
            File cssFile = new File(file.getParent(), StringHelper.replaceFileExt(file.getName(), WebConstants.FILE_EXT_CSS));
            FileHelper.writeTextIfNotMatch(cssFile, source, null);
        }

        return new TextFile(loc, source);
    }
}