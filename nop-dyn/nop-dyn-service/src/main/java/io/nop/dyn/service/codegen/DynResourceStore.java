package io.nop.dyn.service.codegen;

import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DynResourceStore implements IResourceStore {
    private final IResourceStore store;
    private final Consumer<String> prepare;

    public DynResourceStore(IResourceStore store, Consumer<String> prepare) {
        this.store = store;
        this.prepare = prepare;
    }

    @Override
    public synchronized IResource getResource(String path, boolean returnNullIfNotExists) {
        IResource resource = store.getResource(path, true);
        if (resource != null) {
            return resource;
        }
        prepare.accept(path);
        return store.getResource(path, returnNullIfNotExists);
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        return store.getChildren(path);
    }

    @Override
    public synchronized boolean supportSave(String path) {
        return store.supportSave(path);
    }

    @Override
    public synchronized String saveResource(String path, IResource resource, IStepProgressListener listener, Map<String, Object> options) {
        return store.saveResource(path, resource, listener, options);
    }
}
