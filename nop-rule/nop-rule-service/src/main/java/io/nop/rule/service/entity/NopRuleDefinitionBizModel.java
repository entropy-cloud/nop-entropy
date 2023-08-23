
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
    public ConditionFieldsResponse getConditionFields(
            @Name(OrmConstants.PROP_ID) String ruleId, IServiceContext context) {
        NopRuleDefinition rule = get(ruleId, false, context);
        RuleModel ruleModel = ruleModelLoader.buildRuleModel(rule);
        List<Map<String, Object>> fields = ConditionSchemaHelper.schemaToFields(null, ruleModel.getInputSchema());

        ConditionFieldsResponse ret = new ConditionFieldsResponse();
        ret.setFields(fields);
        return ret;
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
