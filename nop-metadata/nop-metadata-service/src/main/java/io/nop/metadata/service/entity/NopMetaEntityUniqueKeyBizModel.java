
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaEntityUniqueKeyBiz;
import io.nop.metadata.dao.entity.NopMetaEntityUniqueKey;

@BizModel("NopMetaEntityUniqueKey")
public class NopMetaEntityUniqueKeyBizModel extends CrudBizModel<NopMetaEntityUniqueKey> implements INopMetaEntityUniqueKeyBiz{
    public NopMetaEntityUniqueKeyBizModel(){
        setEntityName(NopMetaEntityUniqueKey.class.getName());
    }
}
