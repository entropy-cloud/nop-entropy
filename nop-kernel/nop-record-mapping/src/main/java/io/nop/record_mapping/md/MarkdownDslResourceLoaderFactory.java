package io.nop.record_mapping.md;

import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.IResourceObjectLoaderFactory;
import io.nop.core.resource.component.ComponentModelConfig;

import java.util.Map;

public class MarkdownDslResourceLoaderFactory implements IResourceObjectLoaderFactory<Object> {

    @Override
    public IResourceObjectLoader<Object> newResourceObjectLoader(ComponentModelConfig config,
                                                                 Map<String, Object> attributes) {
        String mappingName = (String) attributes.get("mappingName");
        if (mappingName == null)
            throw new IllegalArgumentException("nop.err.record.null-mapping-name:" + config.getModelType());

        return new MarkdownDslResourceLoader(config.getXdefPath(), config.getResolveInDir(), mappingName);
    }
}
