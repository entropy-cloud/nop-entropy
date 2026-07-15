
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaTableJoinBiz;
import io.nop.metadata.dao.entity.NopMetaTableJoin;

@BizModel("NopMetaTableJoin")
public class NopMetaTableJoinBizModel extends CrudBizModel<NopMetaTableJoin> implements INopMetaTableJoinBiz{
    public NopMetaTableJoinBizModel(){
        setEntityName(NopMetaTableJoin.class.getName());
    }
}
