
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaProfilingResultBiz;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;

@BizModel("NopMetaProfilingResult")
public class NopMetaProfilingResultBizModel extends CrudBizModel<NopMetaProfilingResult> implements INopMetaProfilingResultBiz{
    public NopMetaProfilingResultBizModel(){
        setEntityName(NopMetaProfilingResult.class.getName());
    }
}
