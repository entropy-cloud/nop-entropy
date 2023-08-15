package io.nop.rule.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.rule.dao.entity._gen._NopRuleDefinition;

import java.util.HashSet;
import java.util.Set;


@BizObjName("NopRuleDefinition")
public class NopRuleDefinition extends _NopRuleDefinition {
    public NopRuleDefinition() {
    }

    public Set<String> getRoleIds() {
        Set<String> roleIds = new HashSet<>();
        for (NopRuleRole ruleRole : getRuleRoles()) {
            roleIds.add(ruleRole.getRoleId());
        }
        return roleIds;
    }
}
