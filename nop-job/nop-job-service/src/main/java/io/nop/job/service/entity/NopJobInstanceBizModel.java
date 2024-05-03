
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.job.dao.entity.NopJobInstance;

@BizModel("NopJobInstance")
public class NopJobInstanceBizModel extends CrudBizModel<NopJobInstance>{
    public NopJobInstanceBizModel(){
        setEntityName(NopJobInstance.class.getName());
    }
}
