package io.nop.record_mapping.md;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.IResourceObjectLoaderFactory;
import io.nop.core.resource.component.ComponentModelConfig;

import java.util.Map;

import static io.nop.record_mapping.RecordMappingErrors.ARG_MODEL_TYPE;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_NULL_MAPPING_NAME;

public class MarkdownDslResourceLoaderFactory implements IResourceObjectLoaderFactory<Object> {

    @Override
    public IResourceObjectLoader<Object> newResourceObjectLoader(ComponentModelConfig config,
                                                                 Map<String, Object> attributes) {
        String mappingName = (String) attributes.get("mappingName");
        if (mappingName == null)
            throw new NopException(ERR_RECORD_NULL_MAPPING_NAME)
                    .param(ARG_MODEL_TYPE, config.getModelType());

        return new MarkdownDslResourceLoader(config.getXdefPath(), config.getResolveInDir(), mappingName);
    }
}
