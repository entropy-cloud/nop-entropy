package io.nop.web.page;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.IResourceObjectLoaderFactory;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;

import java.util.Map;

public class PageModelLoaderFactory implements IResourceObjectLoaderFactory<Object> {

    @Override
    public IResourceObjectLoader<Object> newResourceObjectLoader(ComponentModelConfig config,
                                                                 Map<String, Object> attributes) {
        return new PageModelLoader();
    }

    public static class PageModelLoader implements IResourceObjectLoader<Object> {
        @Override
        public PageModel loadObjectFromPath(String path) {
            int pos = path.indexOf('|');
            String locale = AppConfig.defaultLocale();
            String resPath = path;
            boolean resolveI18n = false;
            if (pos > 0) {
                locale = path.substring(0, pos);
                resolveI18n = true;
                resPath = path.substring(pos + 1);
            }

            IResource resource = VirtualFileSystem.instance().getResource(resPath);
            return getPageProvider().loadPage(resource, locale, resolveI18n);
        }

        @Override
        public PageModel loadObjectFromResource(IResource resource) {
            return getPageProvider().loadPage(resource, AppConfig.defaultLocale(), false);
        }

        static PageProvider getPageProvider() {
            return BeanContainer.getBeanByType(PageProvider.class);
        }
    }
}
