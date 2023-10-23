/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.dao.store;

import io.nop.core.resource.IResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.store.IWorkflowModelStore;
import io.nop.wf.dao.entity.NopWfDefinition;
import jakarta.inject.Inject;

import java.util.List;

public class DaoWorkflowModelStore implements IWorkflowModelStore {

    private IWorkflowModelStore defaultStore;

    private IDaoProvider daoProvider;

    @Inject
    public void setDefaultStore(IWorkflowModelStore defaultStore) {
        this.defaultStore = defaultStore;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    protected IEntityDao<NopWfDefinition> dao() {
        return daoProvider.daoFor(NopWfDefinition.class);
    }

    @Override
    public Long getLatestVersion(String wfName) {
        return null;
    }

    @Override
    public List<Long> getAllVersions(String wfName) {
        return null;
    }

    @Override
    public IResource getModelResource(String wfName, Long wfVersion) {
        return null;
    }

    @Override
    public IWorkflowModel getModel(String wfName, Long wfVersion) {
        return null;
    }

    @Override
    public void removeModelCache(String wfName, Long wfVersion) {

    }
}
