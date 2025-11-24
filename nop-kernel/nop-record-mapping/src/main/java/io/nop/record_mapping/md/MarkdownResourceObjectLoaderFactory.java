package io.nop.record_mapping.md;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.IResourceObjectLoaderFactory;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.model.RecordMappingConfig;

public class MarkdownResourceObjectLoaderFactory implements IResourceObjectLoaderFactory<Object> {

    @Override
    public IResourceObjectLoader<Object> newResourceObjectLoader(Object config) {
        String mappingName = (String) BeanTool.getProperty(config, "mappingName");
        if (mappingName == null)
            throw new IllegalArgumentException("null mappingName");

        return new IResourceObjectLoader<>() {
            @Override
            public Object loadObjectFromPath(String path) {
                IResource resource = VirtualFileSystem.instance().getResource(path);
                return parseFromResource(resource);
            }

            @Override
            public Object parseFromResource(IResource resource) {
                IRecordMappingManager manager = BeanContainer.getBeanByType(IRecordMappingManager.class);
                RecordMappingConfig config = manager.getRecordMappingConfig(mappingName);
                MarkdownDocument doc = MarkdownTool.instance().parseFromResource(resource);
                return new MappingBasedMarkdownParser(config).parseMarkdown(doc.getRootSection());
            }
        };
    }
}
