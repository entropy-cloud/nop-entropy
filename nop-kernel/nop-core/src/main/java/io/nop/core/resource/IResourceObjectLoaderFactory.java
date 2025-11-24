package io.nop.core.resource;

public interface IResourceObjectLoaderFactory<T> {
    IResourceObjectLoader<T> newResourceObjectLoader(Object config);
}
