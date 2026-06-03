
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;

import io.nop.job.biz.INopJobTaskBiz;
import io.nop.job.dao.entity.NopJobTask;

import static io.nop.job.service.NopJobErrors.ERR_JOB_TASK_DELETE_NOT_ALLOWED;

/**
 * BizModel for NopJobTask. Direct delete operations are blocked because tasks
 * must be managed via the Store layer (IJobTaskStore) to maintain status
 * consistency and lifecycle invariants.
 */
@BizModel("NopJobTask")
public class NopJobTaskBizModel extends CrudBizModel<NopJobTask> implements INopJobTaskBiz{
    public NopJobTaskBizModel(){
        setEntityName(NopJobTask.class.getName());
    }

    @Override
    public boolean delete(String id, io.nop.core.context.IServiceContext context) {
        throw new NopException(ERR_JOB_TASK_DELETE_NOT_ALLOWED)
                .param("jobTaskId", id);
    }
}
