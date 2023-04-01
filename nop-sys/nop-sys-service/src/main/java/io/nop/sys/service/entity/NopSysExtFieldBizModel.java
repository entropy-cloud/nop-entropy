
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysExtField;

@BizModel("NopSysExtField")
public class NopSysExtFieldBizModel extends CrudBizModel<NopSysExtField>{
    public NopSysExtFieldBizModel(){
        setEntityName(NopSysExtField.class.getName());
    }
}
