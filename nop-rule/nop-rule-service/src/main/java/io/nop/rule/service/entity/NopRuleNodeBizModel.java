
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.rule.dao.entity.NopRuleNode;

@BizModel("NopRuleNode")
public class NopRuleNodeBizModel extends CrudBizModel<NopRuleNode> {
    public NopRuleNodeBizModel() {
        setEntityName(NopRuleNode.class.getName());
    }

}
