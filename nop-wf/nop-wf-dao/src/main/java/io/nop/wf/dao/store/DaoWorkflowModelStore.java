package io.nop.wf.dao.store;

import io.nop.core.resource.component.version.IVersionedModelStore;
import io.nop.dao.api.IDaoProvider;
import io.nop.wf.core.model.IWorkflowModel;
import jakarta.inject.Inject;

import java.util.List;

public class DaoWorkflowModelStore implements IVersionedModelStore<IWorkflowModel> {

    private IVersionedModelStore<IWorkflowModel> defaultStore;

    private IDaoProvider daoProvider;

    public void setDefaultStore(IVersionedModelStore<IWorkflowModel> defaultStore) {
        this.defaultStore = defaultStore;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public Long getLatestVersion(String modelName) {
        return null;
    }

    @Override
    public List<Long> getAllVersions(String modelName) {
        return null;
    }

    @Override
    public IWorkflowModel getModel(String modelName, Long modelVersion) {
        return null;
    }

    @Override
    public void removeModelCache(String modelName, Long modelVersion) {

    }
}
