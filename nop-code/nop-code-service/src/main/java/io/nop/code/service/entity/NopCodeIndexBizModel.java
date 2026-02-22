
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.code.biz.INopCodeIndexBiz;
import io.nop.code.dao.entity.NopCodeIndex;

@BizModel("NopCodeIndex")
public class NopCodeIndexBizModel extends CrudBizModel<NopCodeIndex> implements INopCodeIndexBiz{
    public NopCodeIndexBizModel(){
        setEntityName(NopCodeIndex.class.getName());
    }
}
