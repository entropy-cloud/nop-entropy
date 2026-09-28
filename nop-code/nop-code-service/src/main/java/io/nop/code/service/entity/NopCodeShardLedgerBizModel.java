
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.code.biz.INopCodeShardLedgerBiz;
import io.nop.code.dao.entity.NopCodeShardLedger;

@BizModel("NopCodeShardLedger")
public class NopCodeShardLedgerBizModel extends CrudBizModel<NopCodeShardLedger> implements INopCodeShardLedgerBiz{
    public NopCodeShardLedgerBizModel(){
        setEntityName(NopCodeShardLedger.class.getName());
    }
}
