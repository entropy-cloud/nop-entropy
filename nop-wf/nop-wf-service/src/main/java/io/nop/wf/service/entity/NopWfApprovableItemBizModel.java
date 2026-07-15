
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.wf.biz.INopWfApprovableItemBiz;
import io.nop.wf.dao.entity.NopWfApprovableItem;

@BizModel("NopWfApprovableItem")
public class NopWfApprovableItemBizModel extends CrudBizModel<NopWfApprovableItem> implements INopWfApprovableItemBiz{
    public NopWfApprovableItemBizModel(){
        setEntityName(NopWfApprovableItem.class.getName());
    }
}
