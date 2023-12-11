
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.wf.dao.entity.NopWfPage;

@BizModel("NopWfPage")
public class NopWfPageBizModel extends CrudBizModel<NopWfPage>{
    public NopWfPageBizModel(){
        setEntityName(NopWfPage.class.getName());
    }
}
