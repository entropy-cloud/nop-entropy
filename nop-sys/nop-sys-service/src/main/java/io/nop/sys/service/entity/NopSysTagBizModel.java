
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysTag;

@BizModel("NopSysTag")
public class NopSysTagBizModel extends CrudBizModel<NopSysTag>{
    public NopSysTagBizModel(){
        setEntityName(NopSysTag.class.getName());
    }
}
