
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.rule.dao.entity.NopRuleDefinition;

@BizModel("NopRuleDefinition")
public class NopRuleDefinitionBizModel extends CrudBizModel<NopRuleDefinition>{
    public NopRuleDefinitionBizModel(){
        setEntityName(NopRuleDefinition.class.getName());
    }
}
