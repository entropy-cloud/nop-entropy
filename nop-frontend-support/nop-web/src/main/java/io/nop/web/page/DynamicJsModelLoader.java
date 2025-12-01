package io.nop.web.page;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.TextFile;

public class DynamicJsModelLoader implements IResourceObjectLoader<TextFile> {

    @Override
    public TextFile loadObjectFromPath(String path) {
        DynamicJsLoader loader = BeanContainer.getBeanByType(DynamicJsLoader.class);
        return loader.loadDynamicJs(path);
    }
}
