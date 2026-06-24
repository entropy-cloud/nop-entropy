
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.biz.INopSysCompactExtFieldBiz;
import io.nop.sys.dao.entity.NopSysCompactExtField;

@BizModel("NopSysCompactExtField")
public class NopSysCompactExtFieldBizModel extends CrudBizModel<NopSysCompactExtField> implements INopSysCompactExtFieldBiz{
    public NopSysCompactExtFieldBizModel(){
        setEntityName(NopSysCompactExtField.class.getName());
    }
}
