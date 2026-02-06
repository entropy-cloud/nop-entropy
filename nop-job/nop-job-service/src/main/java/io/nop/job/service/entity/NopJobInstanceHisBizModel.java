
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.job.dao.entity.NopJobInstanceHis;
import io.nop.job.biz.INopJobInstanceHisBiz;

@BizModel("NopJobInstanceHis")
public class NopJobInstanceHisBizModel extends CrudBizModel<NopJobInstanceHis> implements INopJobInstanceHisBiz {
    public NopJobInstanceHisBizModel(){
        setEntityName(NopJobInstanceHis.class.getName());
    }
}
