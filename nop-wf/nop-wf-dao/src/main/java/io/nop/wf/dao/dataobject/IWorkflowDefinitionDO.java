package io.nop.wf.dao.dataobject;

import io.nop.dao.api.IDataObject;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.dao.entity.NopWfDefinition;

public interface IWorkflowDefinitionDO extends IDataObject {
    NopWfDefinition getSourceObject();

    default String getWfDefId() {
        return getSourceObject().getWfDefId();
    }

    long getRunningInstanceCount();

    long getAllInstanceCount();

    IWorkflowModel parseWorkflowModel();

    void validateModel();
}
