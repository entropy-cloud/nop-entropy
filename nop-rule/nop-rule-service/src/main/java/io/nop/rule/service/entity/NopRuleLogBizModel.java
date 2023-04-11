
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.rule.dao.entity.NopRuleLog;

@BizModel("NopRuleLog")
public class NopRuleLogBizModel extends CrudBizModel<NopRuleLog>{
    public NopRuleLogBizModel(){
        setEntityName(NopRuleLog.class.getName());
    }
}
