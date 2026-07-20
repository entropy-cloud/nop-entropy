
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaGlossaryBiz;
import io.nop.metadata.dao.entity.NopMetaGlossary;

@BizModel("NopMetaGlossary")
public class NopMetaGlossaryBizModel extends CrudBizModel<NopMetaGlossary> implements INopMetaGlossaryBiz{
    public NopMetaGlossaryBizModel(){
        setEntityName(NopMetaGlossary.class.getName());
    }
}
