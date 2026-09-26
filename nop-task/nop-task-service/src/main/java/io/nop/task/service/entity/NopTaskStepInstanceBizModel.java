
package io.nop.task.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.task.TaskErrors;
import io.nop.task.dao.entity.NopTaskStepInstance;
import io.nop.task.biz.INopTaskStepInstanceBiz;

import java.util.Map;

import static io.nop.biz.BizConstants.BIZ_OBJ_NAME_THIS_OBJ;

@BizModel("NopTaskStepInstance")
public class NopTaskStepInstanceBizModel extends CrudBizModel<NopTaskStepInstance> implements INopTaskStepInstanceBiz {
    public NopTaskStepInstanceBizModel(){
        setEntityName(NopTaskStepInstance.class.getName());
    }

    /**
     * plan 364 [维度04-01]：步骤实例行为引擎独占数据，copyForNew 的 cloneInstance 会整行克隆
     * 引擎状态列绕过 xmeta insert 锁，显式禁用（对齐 nop-auth MfaSensitiveTableBizModel 先例）。
     */
    @Description("Disabled: task step instance rows are engine-owned")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopTaskStepInstance copyForNew(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw crudWriteDisabled("copyForNew");
    }

    protected NopException crudWriteDisabled(String action) {
        return new NopException(TaskErrors.ERR_TASK_CRUD_WRITE_DISABLED)
                .param(TaskErrors.ARG_BIZ_OBJ_NAME, getBizObjName())
                .param(TaskErrors.ARG_ACTION, action);
    }
}
