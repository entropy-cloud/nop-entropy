/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.core.context.IServiceContext;
import io.nop.wf.dao.dataobject.IWorkflowDOProvider;
import io.nop.wf.dao.dataobject.IWorkflowDefinitionDO;
import io.nop.wf.dao.entity.NopWfDefinition;
import io.nop.wf.biz.INopWfDefinitionBiz;
import jakarta.inject.Inject;

@BizModel("NopWfDefinition")
public class NopWfDefinitionBizModel extends CrudBizModel<NopWfDefinition> implements INopWfDefinitionBiz {

    @Inject
    IWorkflowDOProvider workflowDOProvider;

    public NopWfDefinitionBizModel() {
        setEntityName(NopWfDefinition.class.getName());
    }

    @Override
    protected void defaultPrepareSave(EntityData<NopWfDefinition> entityData, IServiceContext context) {
        NopWfDefinition entity = entityData.getEntity();
        if (entity.getIsDeprecated() == null)
            entity.setIsDeprecated(false);

        super.defaultPrepareSave(entityData, context);
    }

    @Override
    protected void defaultPrepareCopyForNew(EntityData<NopWfDefinition> entityData, IServiceContext context) {
        NopWfDefinition entity = entityData.getEntity();
        IWorkflowDefinitionDO defDO = workflowDOProvider.getWorkflowDefinitionDO(entity, context);
        entity.setWfVersion(defDO.getMaxVersion() + 1);

        super.defaultPrepareCopyForNew(entityData, context);
    }
}
