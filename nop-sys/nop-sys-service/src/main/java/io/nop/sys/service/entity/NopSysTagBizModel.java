
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysTag;
import io.nop.sys.biz.INopSysTagBiz;

@BizModel("NopSysTag")
public class NopSysTagBizModel extends CrudBizModel<NopSysTag> implements INopSysTagBiz {
    public NopSysTagBizModel(){
        setEntityName(NopSysTag.class.getName());
    }
}
