
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.rule.dao.entity.NopRuleOutput;

@BizModel("NopRuleOutput")
public class NopRuleOutputBizModel extends CrudBizModel<NopRuleOutput>{
    public NopRuleOutputBizModel(){
        setEntityName(NopRuleOutput.class.getName());
    }
}
