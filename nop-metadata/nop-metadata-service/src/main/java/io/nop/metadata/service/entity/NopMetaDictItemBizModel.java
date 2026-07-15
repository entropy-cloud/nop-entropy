
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaDictItemBiz;
import io.nop.metadata.dao.entity.NopMetaDictItem;

@BizModel("NopMetaDictItem")
public class NopMetaDictItemBizModel extends CrudBizModel<NopMetaDictItem> implements INopMetaDictItemBiz{
    public NopMetaDictItemBizModel(){
        setEntityName(NopMetaDictItem.class.getName());
    }
}
