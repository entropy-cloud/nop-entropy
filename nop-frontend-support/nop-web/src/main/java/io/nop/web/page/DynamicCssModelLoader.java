package io.nop.web.page;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.TextFile;

public class DynamicCssModelLoader implements IResourceObjectLoader<TextFile> {
    @Override
    public TextFile loadObjectFromPath(String path) {
        DynamicCssLoader loader = BeanContainer.getBeanByType(DynamicCssLoader.class);
        return loader.loadDynamicCss(path);
    }
}
