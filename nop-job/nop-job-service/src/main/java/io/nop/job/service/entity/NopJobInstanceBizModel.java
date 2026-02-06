
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.job.dao.entity.NopJobInstance;
import io.nop.job.biz.INopJobInstanceBiz;

@BizModel("NopJobInstance")
public class NopJobInstanceBizModel extends CrudBizModel<NopJobInstance> implements INopJobInstanceBiz {
    public NopJobInstanceBizModel(){
        setEntityName(NopJobInstance.class.getName());
    }
}
