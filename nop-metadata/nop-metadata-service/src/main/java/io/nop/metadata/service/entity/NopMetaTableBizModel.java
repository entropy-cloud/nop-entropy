
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaTableBiz;
import io.nop.metadata.dao.entity.NopMetaTable;

@BizModel("NopMetaTable")
public class NopMetaTableBizModel extends CrudBizModel<NopMetaTable> implements INopMetaTableBiz{
    public NopMetaTableBizModel(){
        setEntityName(NopMetaTable.class.getName());
    }
}
