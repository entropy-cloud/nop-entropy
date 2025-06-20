
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysObjTag;

@BizModel("NopSysObjTag")
public class NopSysObjTagBizModel extends CrudBizModel<NopSysObjTag>{
    public NopSysObjTagBizModel(){
        setEntityName(NopSysObjTag.class.getName());
    }
}
