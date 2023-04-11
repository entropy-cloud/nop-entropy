
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.rule.dao.entity.NopRuleRole;

@BizModel("NopRuleRole")
public class NopRuleRoleBizModel extends CrudBizModel<NopRuleRole>{
    public NopRuleRoleBizModel(){
        setEntityName(NopRuleRole.class.getName());
    }
}
