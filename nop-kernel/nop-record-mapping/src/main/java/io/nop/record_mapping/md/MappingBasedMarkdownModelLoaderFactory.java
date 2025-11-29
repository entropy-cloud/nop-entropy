package io.nop.record_mapping.md;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.impl.RecordMappingManagerImpl;
import io.nop.record_mapping.model.RecordMappingConfig;
import io.nop.xlang.xdsl.IMarkdownModelLoaderFactory;

public class MappingBasedMarkdownModelLoaderFactory implements IMarkdownModelLoaderFactory {
    @Override
    public IResourceObjectLoader<?> newMarkdownModelLoader(String mappingName) {
        return new IResourceObjectLoader<>() {
            @Override
            public Object loadObjectFromPath(String path) {
                return parseFromResource(VirtualFileSystem.instance().getResource(path));
            }

            @Override
            public Object parseFromResource(IResource resource) {
                IRecordMappingManager manager = BeanContainer.isInitialized() ?
                        BeanContainer.getBeanByType(IRecordMappingManager.class)
                        : new RecordMappingManagerImpl();

                MarkdownDocument doc = MarkdownTool.instance().parseFromResource(resource);

                RecordMappingConfig mapping = manager.getRecordMappingConfig(mappingName);
                Object model = new MappingBasedMarkdownParser(mapping).parseMarkdown(doc.getRootSection());
                return model;
            }
        };
    }
}
