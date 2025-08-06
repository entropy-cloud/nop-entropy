
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.job.dao.entity.NopJobInstanceHis;

@BizModel("NopJobInstanceHis")
public class NopJobInstanceHisBizModel extends CrudBizModel<NopJobInstanceHis>{
    public NopJobInstanceHisBizModel(){
        setEntityName(NopJobInstanceHis.class.getName());
    }
}
