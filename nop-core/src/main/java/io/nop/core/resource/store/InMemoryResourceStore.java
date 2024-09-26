/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ProcessResult;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.tree.TreeVisitors;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.resource.impl.InMemoryDirResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.core.resource.impl.UnknownResource;
import io.nop.core.resource.zip.IZipInput;
import io.nop.core.resource.zip.ZipOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_IN_MEMORY_STORE_NOT_ALLOW_SAVE;

public class InMemoryResourceStore implements IResourceStore {
    private final ResourceTreeNode root = new ResourceTreeNode("", new InMemoryDirResource("/"));

    private boolean supportMakeResource = false;

    private boolean supportSave = false;

    public boolean isSupportMakeResource() {
        return supportMakeResource;
    }

    public void setSupportMakeResource(boolean supportMakeResource) {
        this.supportMakeResource = supportMakeResource;
    }

    public void addResource(IResource resource) {
        root.addNode(resource.getPath(), resource);
    }

    public boolean removeResource(IResource resource) {
        return root.removeNode(resource.getPath());
    }

    public boolean addResourceIfAbsent(IResource resource) {
        return root.addNodeIfAbsent(resource.getPath(), resource);
    }

    public void addZipFile(String basePath, IResource zipResource) {
        addZipFile(basePath, zipResource, new ZipOptions());
    }

    public void addZipFile(String basePath, IResource zipResource, ZipOptions options) {
        String normalized = normalizeBasePath(basePath);
        InputStream is = zipResource.getInputStream();
        try {
            IZipInput input = ResourceHelper.getZipTool().newZipInput(is, options);
            input.unzip((entry, stream) -> {
                if (entry.getName().endsWith("/"))
                    return ProcessResult.CONTINUE;

                String path = StringHelper.appendPath(normalized, entry.getName());
                try {
                    byte[] bytes = IoHelper.readBytes(stream);
                    addResource(new ByteArrayResource(path, bytes, entry.getTime()));
                    return ProcessResult.CONTINUE;
                } catch (IOException e) {
                    throw NopException.adapt(e);
                }
            });
            input.close();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(is);
        }
    }

    String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isEmpty())
            return "/";
        if (!basePath.startsWith("/") && basePath.indexOf(':') < 0)
            return "/" + basePath;
        return basePath;
    }

    @Override
    public IResource getResource(String path, boolean returnNullIfNotExists) {
        ResourceTreeNode node = root.getNode(path);
        if (node == null) {
            if (returnNullIfNotExists)
                return null;
            return getNotExistsResource(path);
        }
        return node.getResource();
    }

    protected IResource getNotExistsResource(String path) {
        return new UnknownResource(path);
    }

    @Override
    public IResource makeResource(String path) {
        if (!supportMakeResource)
            return getResource(path);
        IResource resource = new InMemoryTextResource(path, "");
        addResource(resource);
        return resource;
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        ResourceTreeNode node = root.getNode(path);
        if (node == null)
            return null;

        return node.getChildResources();
    }

    @Override
    public Collection<IResource> getAllResources(String path, String suffix) {
        ResourceTreeNode node = root.getNode(path);
        if (node == null)
            return null;

        return node.getAllResourcesWithSuffix(suffix);
    }

    @Override
    public boolean supportSave(String path) {
        return supportSave;
    }

    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener,
                               Map<String, Object> options) {
        throw new NopException(ERR_RESOURCE_IN_MEMORY_STORE_NOT_ALLOW_SAVE).param(ARG_RESOURCE_PATH, path);

        // String retPath = StringHelper.appendPath(path, resource.getName());
        // if (!resource.getPath().equals(retPath)) {
        // resource = new DelegateResource(retPath, resource);
        // }
        // ResourceTreeNode node = root.mkdirs(path);
        // node.addChild(resource.getName(), resource);
        // return retPath;
    }

    public void saveToResourceStore(IResourceStore resourceStore) {
        saveToResourceStore(resourceStore, null);
    }

    public void saveToResourceStore(IResourceStore resourceStore, Predicate<IResource> filter) {
        Predicate<ResourceTreeNode> nodeFilter = filter == null ? null : node -> filter.test(node.getResource());

        Iterator<ResourceTreeNode> it = TreeVisitors.depthFirstIterator(root, false, nodeFilter);
        while (it.hasNext()) {
            IResource resource = it.next().getResource();
            resourceStore.saveResource(resource.getPath(), resource, null, null);
        }
    }

    public void merge(InMemoryResourceStore store) {
        this.merge(store, null);
    }

    public void merge(InMemoryResourceStore store, Predicate<IResource> filter) {
        this.root.merge(store.root, filter);
    }
}