/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.UnknownResource;
import io.nop.core.resource.tenant.ResourceTenantManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static io.nop.core.CoreErrors.ARG_CURRENT_PATH;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_CURRENT_PATH_CONTAINS_INVALID_DELTA_LAYER_ID;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_DELTA_LAYER_ID;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_ALLOW_ACCESS_INTERNAL_PATH;

/**
 * 在普通文件系统之上建立的差量文件系统。差量文件系统对外暴露的虚拟路径内部由多个层组成，每一层对应一个虚拟目录。例如
 * layerIds=app,product,platform时，解析虚拟路径/nop/commons/a.html时会依次尝试如下路径 1. /_delta/app/nop/commons/a.html 2.
 * /_delta/product/nop/commons/a.html 3. /_delta/platform/nop/commons/a.html 4. /nop/commons/a.html
 * <p>
 * 如果指定了useTenantStore, 则首先会尝试获取/_tenant/{tenantId}/nop/commons/a.html文件，如果未找到，再尝试以上查找逻辑。
 * <p>
 * 差量定制的多个层构成覆盖关系，程序逻辑自上而下的查找定制层，直到在某一层找到目标文件为止
 */
public class DeltaResourceStore implements IDeltaResourceStore {
    /**
     * 一个展平的文件系统，它的_delta子目录对应差量定制层
     */
    private IResourceStore store;

    /**
     * 对应于/_delta/目录下的子目录名
     */
    private List<String> deltaLayerIds;

    private ITenantResourceStoreSupplier tenantStoreSupplier = ResourceTenantManager.instance();


    private boolean useInMemoryLayer;

    private Set<String> classPathFiles;

    public IResourceStore getStore() {
        return store;
    }

    public void setStore(IResourceStore store) {
        this.store = store;
    }

    public List<String> getDeltaLayerIds() {
        return deltaLayerIds;
    }

    public void setDeltaLayerIds(List<String> deltaLayerIds) {
        this.deltaLayerIds = deltaLayerIds;
    }

    public ITenantResourceStoreSupplier getTenantStoreSupplier() {
        return tenantStoreSupplier;
    }

    public void setTenantStoreSupplier(ITenantResourceStoreSupplier tenantStoreSupplier) {
        this.tenantStoreSupplier = tenantStoreSupplier;
    }

    @Override
    public void updateInMemoryLayer(IResourceStore store) {
        Guard.notNull(store, "inMemoryLayer");

        if (this.useInMemoryLayer) {
            OverrideResourceStore overrideStore = (OverrideResourceStore) this.store;
            this.store = new OverrideResourceStore(store, overrideStore.getSecondStore());
        } else {
            this.useInMemoryLayer = true;
            this.store = new OverrideResourceStore(store, this.store);
        }
    }

    @Override
    public IResourceStore getInMemoryLayer() {
        if (this.useInMemoryLayer)
            return ((OverrideResourceStore) store).getFirstStore();
        return null;
    }

    @Override
    public Set<String> getClassPathFiles() {
        return classPathFiles;
    }

    public void setClassPathFiles(Set<String> classPathFiles) {
        this.classPathFiles = classPathFiles;
    }

    @Override
    public IResource getResource(String path, boolean returnNullIfNotExists) {
        Guard.checkArgument(ResourceHelper.isNormalVirtualPath(path), "path must be normal virtual path");
        if (ResourceTenantManager.instance().isEnableTenantResource()) {
            String tenantId = ContextProvider.currentTenantId();
            if (tenantId != null) {
                IResourceStore store = getTenantStore(tenantId);
                if (store != null) {
                    String fullPath = ResourceHelper.buildTenantPath(tenantId, path);
                    IResource resource = store.getResource(fullPath);
                    if (resource.exists())
                        return resource;
                }
            }
        }

        if (deltaLayerIds != null) {
            for (int i = 0, n = deltaLayerIds.size(); i < n; i++) {
                String deltaLayerId = deltaLayerIds.get(i);
                String fullPath = ResourceHelper.buildDeltaPath(deltaLayerId, path);
                if (fullPath.endsWith("/"))
                    fullPath = StringHelper.removeTail(fullPath, "/");
                IResource resource = store.getResource(fullPath);
                if (resource.exists())
                    return resource;
            }
        }

        IResource resource = store.getResource(path, returnNullIfNotExists);
        return resource;
    }

    IResourceStore getTenantStore(String tenantId) {
        if (tenantStoreSupplier != null)
            return tenantStoreSupplier.getTenantResourceStore(tenantId);
        return store;
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        path = ResourceHelper.getStdPath(path);
        Map<String, IResource> map = new TreeMap<>();
        if (!path.equals("/")) {
            if (ResourceTenantManager.instance().isEnableTenantResource()) {
                String tenantId = ContextProvider.currentTenantId();
                if (tenantId != null) {
                    IResourceStore store = getTenantStore(tenantId);
                    if (store != null) {
                        String fullPath = ResourceHelper.buildTenantPath(tenantId, path);
                        addResource(map, store, fullPath);
                    }
                }
            }

            if (deltaLayerIds != null) {
                for (int i = 0, n = deltaLayerIds.size(); i < n; i++) {
                    String deltaLayerId = deltaLayerIds.get(i);
                    String fullPath = ResourceHelper.buildDeltaPath(deltaLayerId, path);
                    addResource(map, store, fullPath);
                }
            }
        }

        addResource(map, store, path);
        if (path.equals("/")) {
            // 根目录下所有以_为前缀的目录是内部使用的隐藏目录，都不对外暴露
            map.keySet().removeIf(name -> name.startsWith("_"));
        }
        return new ArrayList<>(map.values());
    }

    void addResource(Map<String, IResource> map, IResourceStore store, String fullPath) {
        List<? extends IResource> children = store.getChildren(fullPath);
        if (children != null) {
            for (IResource child : children) {
                map.putIfAbsent(child.getName(), child);
            }
        }
    }

    @Override
    public boolean supportSave(String path) {
        return true;
    }

    /**
     * 只有最上层的层为可写层
     */
    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener,
                               Map<String, Object> options) {
        if (ResourceTenantManager.instance().isEnableTenantResource()) {
            String tenantId = ContextProvider.currentTenantId();
            if (tenantId != null && ResourceTenantManager.supportTenant(path)) {
                String fullPath = ResourceHelper.buildTenantPath(tenantId, path);
                IResourceStore store = getTenantStore(tenantId);
                if (store != null)
                    return store.saveResource(fullPath, resource, listener, options);
            }
        }
        if (deltaLayerIds != null && !deltaLayerIds.isEmpty()) {
            String deltaLayerId = deltaLayerIds.get(0);
            String fullPath = ResourceHelper.buildDeltaPath(deltaLayerId, path);
            return store.saveResource(fullPath, resource, listener, options);
        }
        return store.saveResource(path, resource, listener, options);
    }

    /**
     * 仅在tenant层查找
     */
    public IResource getTenantResource(String path) {
        String tenantId = ContextProvider.currentTenantId();
        if (tenantId != null) {
            IResourceStore store = getTenantStore(tenantId);
            if (store != null) {
                String fullPath = ResourceHelper.buildTenantPath(tenantId, path);
                IResource resource = store.getResource(fullPath);
                return resource;
            }
        }
        return new UnknownResource(path);
    }

    /**
     * 仅在当前层的下层查找
     */
    @Override
    public IResource getSuperResource(String currentPath, boolean returnNullIfNotExists) {
        String path = ResourceHelper.getStdPath(currentPath);

        int deltaIndex = -1;

        if (deltaLayerIds != null) {
            deltaIndex = deltaLayerIds.size();
            if (!ResourceHelper.isTenantPath(currentPath)) {
                String deltaId = ResourceHelper.getDeltaLayerId(currentPath);
                if (deltaId != null) {
                    int index = deltaLayerIds.indexOf(deltaId);
                    if (index < 0)
                        throw new NopException(ERR_RESOURCE_CURRENT_PATH_CONTAINS_INVALID_DELTA_LAYER_ID)
                                .param(ARG_CURRENT_PATH, currentPath);
                    deltaIndex = index;
                } else {
                    deltaIndex = -1;
                }
            }
        }
        // 没有tenant层， 也没有delta层
        if (deltaIndex < 0) {
            // 只有租户层，没有delta层，则租户层的super对应stdPath
            if(ResourceHelper.isTenantPath(currentPath))
                return store.getResource(path, returnNullIfNotExists);

            if (returnNullIfNotExists)
                return null;
            return new UnknownResource(ResourceConstants.SUPER_NS + ':' + path);
        }

        if (deltaLayerIds != null) {
            for (int i = deltaIndex + 1, n = deltaLayerIds.size(); i < n; i++) {
                String deltaLayerId = deltaLayerIds.get(i);
                String fullPath = ResourceHelper.buildDeltaPath(deltaLayerId, path);
                IResource resource = store.getResource(fullPath);
                if (resource.exists())
                    return resource;
            }
        }

        IResource resource = store.getResource(path, returnNullIfNotExists);
        return resource;
    }

    @Override
    public IResource getRawResource(String path) {
        if (ResourceHelper.isTenantPath(path)) {
            int pos = path.indexOf('/', ResourceConstants.TENANT_PATH_PREFIX.length());
            String tenantId = path.substring(ResourceConstants.TENANT_PATH_PREFIX.length(), pos);

            IResourceStore store = getTenantStore(tenantId);
            if (store == null)
                return new UnknownResource(path);
            return store.getResource(path);
        }

        if (ResourceHelper.isDeltaPath(path)) {
            String deltaLayerId = ResourceHelper.getDeltaLayerId(path);
            if (deltaLayerId == null || !deltaLayerIds.contains(deltaLayerId))
                throw new NopException(ERR_RESOURCE_INVALID_DELTA_LAYER_ID).param(ARG_RESOURCE_PATH, path);
        } else {
            if (path.startsWith(ResourceConstants.INTERNAL_PATH_PREFIX))
                throw new NopException(ERR_RESOURCE_NOT_ALLOW_ACCESS_INTERNAL_PATH).param(ARG_RESOURCE_PATH, path);

            ResourceHelper.checkNormalVirtualPath(path);
        }
        return store.getResource(path);
    }
}