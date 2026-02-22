
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.code.biz.INopCodeUsageBiz;
import io.nop.code.dao.entity.NopCodeUsage;

@BizModel("NopCodeUsage")
public class NopCodeUsageBizModel extends CrudBizModel<NopCodeUsage> implements INopCodeUsageBiz{
    public NopCodeUsageBizModel(){
        setEntityName(NopCodeUsage.class.getName());
    }
}
