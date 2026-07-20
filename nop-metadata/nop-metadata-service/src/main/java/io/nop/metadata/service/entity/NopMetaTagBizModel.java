
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaTagBiz;
import io.nop.metadata.dao.entity.NopMetaTag;

@BizModel("NopMetaTag")
public class NopMetaTagBizModel extends CrudBizModel<NopMetaTag> implements INopMetaTagBiz{
    public NopMetaTagBizModel(){
        setEntityName(NopMetaTag.class.getName());
    }
}
