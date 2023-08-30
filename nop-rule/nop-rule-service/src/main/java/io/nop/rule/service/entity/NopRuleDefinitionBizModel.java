
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.dao.entity.NopRuleDefinition;
import io.nop.rule.dao.model.DaoRuleModelLoader;
import io.nop.rule.service.NopRuleConstants;
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
    public ConditionFieldsResponse getConditionFields(
            @Name(NopRuleConstants.RULE_ID_NAME) String ruleId, IServiceContext context) {
        NopRuleDefinition rule = get(ruleId, false, context);
        RuleModel ruleModel = ruleModelLoader.buildRuleModel(rule);
        List<Map<String, Object>> fields = ConditionSchemaHelper.schemaToFields(null, ruleModel.getInputSchema());

        ConditionFieldsResponse ret = new ConditionFieldsResponse();
        ret.setFields(fields);
        return ret;
    }

    @BizQuery
    public DictBean getOutputFields(@Name(NopRuleConstants.RULE_ID_NAME) String ruleId, IServiceContext context) {
        NopRuleDefinition rule = get(ruleId, false, context);
        List<Map<String, Object>> outputs = rule.getRuleOutputs();
        DictBean dict = new DictBean();
        if (outputs == null) {
            DictOptionBean option = new DictOptionBean();
            option.setLabel(NopRuleConstants.VAR_RESULT);
            option.setValue(NopRuleConstants.VAR_RESULT);
        } else {
            for (Map<String, Object> output : outputs) {
                DictOptionBean option = new DictOptionBean();
                String name = (String) output.get(NopRuleConstants.VAR_NAME);
                String displayName = (String) output.get(NopRuleConstants.VAR_DISPLAY_NAME);
                if (displayName == null)
                    displayName = name;
                option.setValue(name);
                option.setLabel(displayName);
            }
        }
        return dict;
    }
//
//    @Override
//    protected void defaultPrepareSave(EntityData<NopRuleDefinition> entityData, IServiceContext context) {
//        super.defaultPrepareSave(entityData, context);
//
//        NopRuleDefinition entity = entityData.getEntity();
//        checkRoles(entity, context);
//    }
//
//    @Override
//    protected void defaultPrepareUpdate(EntityData<NopRuleDefinition> entityData, IServiceContext context) {
//        super.defaultPrepareUpdate(entityData, context);
//        checkRoles(entityData.getEntity(), context);
//    }
//
//    private void checkRoles(NopRuleDefinition entity, IServiceContext context) {
//        IUserContext userContext = context.getUserContext();
//        Set<String> roleIds = entity.getRoleIds();
//        if (roleIds.isEmpty())
//            throw new NopException(ERR_RULE_NOT_ASSIGN_ROLES_FOR_RULE)
//                    .param(ARG_RULE_NAME, entity.getRuleName());
//
//        if (!userContext.isUserInAnyRole(roleIds))
//            throw new NopException(ERR_RULE_CREATER_MUST_IN_ROLE_SET)
//                    .param(ARG_RULE_NAME, entity.getRuleName())
//                    .param(ARG_USER_ROLES, userContext.getRoles());
//    }
}
