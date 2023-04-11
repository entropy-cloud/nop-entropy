
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.rule.dao.entity.NopRuleInput;

@BizModel("NopRuleInput")
public class NopRuleInputBizModel extends CrudBizModel<NopRuleInput>{
    public NopRuleInputBizModel(){
        setEntityName(NopRuleInput.class.getName());
    }
}
