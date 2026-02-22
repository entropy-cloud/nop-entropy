
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.code.biz.INopCodeInheritanceBiz;
import io.nop.code.dao.entity.NopCodeInheritance;

@BizModel("NopCodeInheritance")
public class NopCodeInheritanceBizModel extends CrudBizModel<NopCodeInheritance> implements INopCodeInheritanceBiz{
    public NopCodeInheritanceBizModel(){
        setEntityName(NopCodeInheritance.class.getName());
    }
}
