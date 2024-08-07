package io.nop.wf.dao.dataobject;

import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.dao.entity.NopWfDefinition;
import jakarta.inject.Inject;

public class DefaultWorkflowDOProvider implements IWorkflowDOProvider {
    private IDaoProvider daoProvider;
    private IWorkflowManager workflowManager;
    private IOrmTemplate ormTemplate;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setWorkflowManager(IWorkflowManager workflowManager) {
        this.workflowManager = workflowManager;
    }

    @Override
    public IWorkflowDefinitionDO getWorkflowDefinitionDO(NopWfDefinition entity, IServiceContext context) {
        IWorkflowDefinitionDO definitionDO = entity.computeIfAbsent(
                IWorkflowDefinitionDO.class.getSimpleName(),
                k -> new WorkflowDefinitionDO(daoProvider, ormTemplate,
                        workflowManager, entity, context));
        return definitionDO;
    }
}
