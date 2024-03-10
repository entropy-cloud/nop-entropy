/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.resource;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.api.DaoProvider;
import io.nop.orm.OrmConstants;

import static io.nop.orm.OrmConfigs.CFG_DAO_RESOURCE_CHECK_INTERVAL;
import static io.nop.orm.OrmErrors.ARG_PATH;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_DAO_PATH;

public class DaoResourceNamespaceHandler implements IResourceNamespaceHandler {
    public DaoResourceNamespaceHandler(){

    }

    @Override
    public String getNamespace() {
        return OrmConstants.NAMESPACE_DAO;
    }

    public void register() {
        VirtualFileSystem.instance().registerNamespaceHandler(this);
    }

    public void unregister() {
        VirtualFileSystem.instance().unregisterNamespaceHandler(this);
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        String path = ResourceHelper.removeNamespace(vPath, OrmConstants.NAMESPACE_DAO);
        int pos = path.indexOf('/');
        if (pos < 0)
            throw new NopException(ERR_ORM_INVALID_DAO_PATH)
                    .param(ARG_PATH, vPath);

        String entityName = path.substring(0, pos);
        String entityId = path.substring(pos + 1);
        long checkInterval = CFG_DAO_RESOURCE_CHECK_INTERVAL.get();
        return new DaoEntityResource(entityName, entityId, checkInterval, DaoProvider.instance());
    }
}
