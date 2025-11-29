package io.nop.xlang.xdsl;

import io.nop.core.resource.IResourceObjectLoader;

public interface IMarkdownModelLoaderFactory {
    IResourceObjectLoader<?> newMarkdownModelLoader(String mappingName);
}
