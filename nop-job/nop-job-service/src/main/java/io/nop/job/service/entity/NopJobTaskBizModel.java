
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.job.biz.INopJobTaskBiz;
import io.nop.job.dao.entity.NopJobTask;

@BizModel("NopJobTask")
public class NopJobTaskBizModel extends CrudBizModel<NopJobTask> implements INopJobTaskBiz{
    public NopJobTaskBizModel(){
        setEntityName(NopJobTask.class.getName());
    }
}
