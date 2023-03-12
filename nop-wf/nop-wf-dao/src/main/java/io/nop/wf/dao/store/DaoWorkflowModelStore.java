package io.nop.wf.dao.store;

import io.nop.core.resource.IResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.store.IWorkflowModelStore;
import io.nop.wf.dao.entity.NopWfDefinition;

import javax.inject.Inject;
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
    public String getLatestVersion(String wfName) {
        return null;
    }

    @Override
    public List<String> getAllVersions(String wfName) {
        return null;
    }

    @Override
    public IResource getModelResource(String wfName, String wfVersion) {
        return null;
    }

    @Override
    public IWorkflowModel getWorkflowModel(String wfName, String wfVersion) {
        return null;
    }

    @Override
    public void removeModelCache(String wfName, String wfVersion) {

    }
}
