
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.orm.OrmConstants;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.dao.entity.NopRuleDefinition;
import io.nop.rule.dao.model.DaoRuleModelLoader;
import io.nop.web.page.condition.ConditionSchemaHelper;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@BizModel("NopRuleDefinition")
public class NopRuleDefinitionBizModel extends CrudBizModel<NopRuleDefinition> {
    public NopRuleDefinitionBizModel() {
        setEntityName(NopRuleDefinition.class.getName());
    }

    @Inject
    DaoRuleModelLoader ruleModelLoader;

    @BizQuery
    public List<Map<String, Object>> getConditionFields(
            @Name(OrmConstants.PROP_ID) String ruleId, IServiceContext context) {
        NopRuleDefinition rule = get(ruleId, false, context);
        RuleModel ruleModel = ruleModelLoader.buildRuleModel(rule);
        return ConditionSchemaHelper.schemaToFields(null, ruleModel.getInputSchema());
    }
}
