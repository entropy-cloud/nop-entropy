package io.nop.wf.dao.dataobject;

import io.nop.core.context.IServiceContext;
import io.nop.wf.dao.entity.NopWfDefinition;

public interface IWorkflowDOProvider {
    IWorkflowDefinitionDO getWorkflowDefinitionDO(NopWfDefinition entity, IServiceContext context);
}
