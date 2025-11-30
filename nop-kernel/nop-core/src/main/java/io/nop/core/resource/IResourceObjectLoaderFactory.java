package io.nop.core.resource;

import io.nop.core.resource.component.ComponentModelConfig;

import java.util.Map;

public interface IResourceObjectLoaderFactory<T> {
    IResourceObjectLoader<T> newResourceObjectLoader(ComponentModelConfig config, Map<String, Object> attributes);
}
