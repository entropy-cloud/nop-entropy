
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysObjTag;
import io.nop.sys.biz.INopSysObjTagBiz;

@BizModel("NopSysObjTag")
public class NopSysObjTagBizModel extends CrudBizModel<NopSysObjTag> implements INopSysObjTagBiz {
    public NopSysObjTagBizModel(){
        setEntityName(NopSysObjTag.class.getName());
    }
}
