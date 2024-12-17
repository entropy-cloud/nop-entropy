/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.lang.IRefreshable;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.IVirtualFileSystem;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

import static io.nop.core.CoreErrors.ARG_NAMESPACE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_UNKNOWN_NAMESPACE;

public class DefaultVirtualFileSystem implements IVirtualFileSystem, IRefreshable {
    private Map<String, IResourceNamespaceHandler> namespaceHandlers = new ConcurrentHashMap<>();

    private IDeltaResourceStore deltaResourceStore;
    private List<ZipFile> zipFiles;

    public DefaultVirtualFileSystem() {
        registerNamespaceHandler(new SuperNamespaceHandler());
        registerNamespaceHandler(new RawNamespaceHandler());
        registerNamespaceHandler(DumpNamespaceHandler.INSTANCE);
        registerNamespaceHandler(TempNamespaceHandler.INSTANCE);
        registerNamespaceHandler(FileNamespaceHandler.INSTANCE);
        registerNamespaceHandler(ClassPathNamespaceHandler.INSTANCE);
        registerNamespaceHandler(ModuleNamespaceHandler.INSTANCE);
        registerNamespaceHandler(DynamicNamespaceHandler.INSTANCE);
        registerNamespaceHandler(VirtualNamespaceHandler.INSTANCE);
        registerNamespaceHandler(DevResourceNamespaceHandler.INSTANCE);
        registerNamespaceHandler(DataNamespaceHandler.INSTANCE);

        this.buildResourceStore();
    }

    @Override
    public void updateInMemoryLayer(IResourceStore layer) {
        this.deltaResourceStore.updateInMemoryLayer(layer);
    }

    @Override
    public IResourceStore getInMemoryLayer() {
        return deltaResourceStore.getInMemoryLayer();
    }

    @Override
    public synchronized void refresh(boolean refreshDepends) {
        IoHelper.safeCloseAll(zipFiles);
        zipFiles = null;

        this.buildResourceStore();
    }

    protected void buildResourceStore() {
        VfsConfig config = VfsConfigLoader.loadDefault();
        IDeltaResourceStoreBuilder builder = (IDeltaResourceStoreBuilder) ClassHelper.safeNewInstance(config.getStoreBuilderClass());
        this.deltaResourceStore = builder.build(config);
        this.zipFiles = builder.getZipFiles();
    }

    @Override
    public void destroy() {
        DeltaResourceStore deltaResourceStore = new DeltaResourceStore();
        deltaResourceStore.setStore(new InMemoryResourceStore());
        this.deltaResourceStore = deltaResourceStore;

        IoHelper.safeCloseAll(zipFiles);
        zipFiles = null;
    }

    @Override
    public Set<String> getClassPathResources() {
        return deltaResourceStore.getClassPathFiles();
    }

    class SuperNamespaceHandler implements IResourceNamespaceHandler {
        @Override
        public String getNamespace() {
            return ResourceConstants.RESOURCE_NS_SUPER;
        }

        @Override
        public IResource getResource(String path, IResourceStore locator) {
            path = ResourceHelper.removeNamespace(path, getNamespace());
            return deltaResourceStore.getSuperResource(path, false);
        }
    }

    class RawNamespaceHandler implements IResourceNamespaceHandler {
        @Override
        public String getNamespace() {
            return ResourceConstants.RESOURCE_NS_RAW;
        }

        @Override
        public IResource getResource(String path, IResourceStore locator) {
            path = ResourceHelper.removeNamespace(path, getNamespace());
            return deltaResourceStore.getRawResource(path);
        }
    }

    @Override
    public void registerNamespaceHandler(@Nonnull IResourceNamespaceHandler handler) {
        namespaceHandlers.put(handler.getNamespace(), handler);
    }

    @Override
    public void unregisterNamespaceHandler(@Nonnull IResourceNamespaceHandler handler) {
        namespaceHandlers.remove(handler.getNamespace(), handler);
    }

    public IResource getRawResource(String path, boolean returnNullIfNotExists) {
        if (ResourceHelper.hasNamespace(path))
            return getResource(path, returnNullIfNotExists);

        return deltaResourceStore.getRawResource(path);
    }

    @Override
    public IResource getResource(String path, boolean returnNullIfNotExists) {
        String ns = ResourceHelper.getPathNamespace(path);
        if (ns != null) {
            IResourceNamespaceHandler handler = namespaceHandlers.get(ns);
            if (handler == null) {
                if (!StringHelper.isValidVPath(path)) {
                    throw new NopException(ERR_RESOURCE_INVALID_PATH).param(ARG_RESOURCE_PATH, path);
                }

                throw new NopException(ERR_RESOURCE_UNKNOWN_NAMESPACE).param(ARG_RESOURCE_PATH, path)
                        .param(ARG_NAMESPACE, ns);
            }
            IResource resource = handler.getResource(path, deltaResourceStore);
            if (returnNullIfNotExists) {
                if (!resource.exists())
                    return null;
            }
            return resource;
        }

        ResourceHelper.checkNormalVirtualPath(path);

        return deltaResourceStore.getResource(path, returnNullIfNotExists);
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        String ns = ResourceHelper.getPathNamespace(path);
        if (ns != null) {
            IResource resource = getResource(path, true);
            if (resource == null)
                return null;
            if (ResourceHelper.hasNamespace(resource.getPath())) {
                if (resource instanceof IFile) {
                    return ((IFile) resource).getChildren();
                }
                return null;
            }
            path = resource.getStdPath();
        }
        return deltaResourceStore.getChildren(path);
    }

    @Override
    public boolean supportSave(String path) {
        return deltaResourceStore.supportSave(path);
    }

    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener,
                               Map<String, Object> options) {
        String ns = ResourceHelper.getPathNamespace(path);
        if (ns != null) {
            IResource dir = getResource(path, false);
            if (ResourceHelper.hasNamespace(dir.getPath())) {
                String fullPath = StringHelper.appendPath(dir.getPath(), resource.getName());
                resource.saveToResource(getResource(fullPath), listener);
                return fullPath;
            }
            path = dir.getPath();
        }

        ResourceHelper.checkNormalVirtualPath(path);

        return deltaResourceStore.saveResource(path, resource, listener, options);
    }
}