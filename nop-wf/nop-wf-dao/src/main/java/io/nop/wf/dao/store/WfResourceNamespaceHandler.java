/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.dao.store;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.core.resource.component.version.VersionedName;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.core.resource.impl.UnknownResource;
import io.nop.wf.dao.NopWfDaoConstants;
import io.nop.wf.dao.entity.NopWfDefinition;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

public class WfResourceNamespaceHandler implements IResourceNamespaceHandler {

    private DaoWorkflowModelLoader daoWorkflowModelLoader;

    @Inject
    public void setDaoWorkflowModelLoader(DaoWorkflowModelLoader daoProvider) {
        this.daoWorkflowModelLoader = daoProvider;
    }

    @PostConstruct
    public void init() {
        VirtualFileSystem.instance().registerNamespaceHandler(this);
    }

    @PreDestroy
    public void destroy() {
        VirtualFileSystem.instance().unregisterNamespaceHandler(this);
    }

    @Override
    public String getNamespace() {
        return NopWfDaoConstants.NAMESPACE_WF;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        String path = ResourceHelper.removeNamespace(vPath, getNamespace());
        ResourceHelper.checkNormalVirtualPath(path);

        VersionedName versionedName = ResourceVersionHelper.parseVersionedName(path, "/", true);
        NopWfDefinition wfDef = daoWorkflowModelLoader.loadWfDefinition(versionedName.getName(), versionedName.getVersion());
        if (wfDef == null)
            return new UnknownResource(vPath);

        InMemoryTextResource res = new InMemoryTextResource(vPath, wfDef.getModelText());
        if (wfDef.getUpdateTime() != null)
            res.setLastModified(wfDef.getUpdateTime().getTime());
        return res;
    }
}