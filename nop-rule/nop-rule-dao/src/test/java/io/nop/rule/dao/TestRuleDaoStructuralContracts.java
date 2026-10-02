package io.nop.rule.dao;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.rule.dao.entity.NopRuleDefinition;
import io.nop.rule.dao.entity.NopRuleNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构性契约测试：nop-rule-dao 的常量/错误码/实体属性名契约。规则类型（TREE/MATX）、
 * 发布状态、资源路径格式、决策树节点的树形结构与规则定义的主键属性。
 */
public class TestRuleDaoStructuralContracts {

    @Test
    public void testRuleTypeConstants() {
        assertEquals("TREE", NopRuleDaoConstants.RULE_TYPE_TREE, "决策树类型常量");
        assertEquals("MATX", NopRuleDaoConstants.RULE_TYPE_MATX, "决策矩阵类型常量");
    }

    @Test
    public void testActiveStatusAndResourceContract() {
        assertEquals(1, NopRuleDaoConstants.RULE_STATUS_ACTIVE,
                "status=1 表示规则已发布（DaoRuleModelLoader 只加载 ACTIVE 规则）");
        assertEquals("/nop/schema/rule.xdef", NopRuleDaoConstants.XDEF_PATH_RULE,
                "规则 DSL 的 xdef 校验路径");
        assertEquals("inputs", NopRuleDaoConstants.INPUTS_NAME);
        assertEquals("outputs", NopRuleDaoConstants.OUTPUTS_NAME);
        assertEquals("decisionMatrix", NopRuleDaoConstants.DECISION_MATRIX_NAME);
        assertEquals("rule", NopRuleDaoConstants.RULE_TAG_NAME);
    }

    @Test
    public void testRuleErrorCodeSemantics() {
        ErrorCode unknown = NopRuleErrors.ERR_RULE_UNKNOWN_RULE_DEFINITION;
        assertEquals("nop.err.rule.unknown-rule-definition", unknown.getErrorCode());
        assertTrue(Arrays.asList(unknown.getArgNames()).contains(NopRuleErrors.ARG_RULE_NAME),
                "未知规则错误必须携带 ruleName 参数");
        assertTrue(Arrays.asList(unknown.getArgNames()).contains(NopRuleErrors.ARG_RULE_GROUP),
                "未知规则错误必须携带 ruleGroup 参数");

        ErrorCode invalidPath = NopRuleErrors.ERR_RULE_INVALID_DAO_RESOURCE_PATH;
        assertEquals("nop.err.rule.invalid-dao-resource-path", invalidPath.getErrorCode());
        assertTrue(Arrays.asList(invalidPath.getArgNames()).contains(NopRuleErrors.ARG_PATH),
                "非法资源路径错误必须携带 path 参数");
        assertTrue(invalidPath.getDescription().contains("ruleName/ruleVersion"),
                "资源路径错误描述必须说明 ruleName/ruleVersion 两段格式");
    }

    @Test
    public void testRuleDefinitionIdentityProps() {
        assertEquals("ruleName", NopRuleDefinition.PROP_NAME_ruleName);
        assertEquals("ruleVersion", NopRuleDefinition.PROP_NAME_ruleVersion);
        assertEquals("status", NopRuleDefinition.PROP_NAME_status);
        assertEquals("ruleId", NopRuleDefinition.PROP_NAME_ruleId);
        assertEquals("modelText", NopRuleDefinition.PROP_NAME_modelText,
                "规则 DSL 文本存储在 modelText 属性");
        assertEquals("ruleType", NopRuleDefinition.PROP_NAME_ruleType);
    }

    @Test
    public void testRuleNodeTreeStructureProps() {
        assertEquals("ruleId", NopRuleNode.PROP_NAME_ruleId);
        assertEquals("parentId", NopRuleNode.PROP_NAME_parentId);
        assertEquals("isLeaf", NopRuleNode.PROP_NAME_isLeaf);
        assertEquals("predicate", NopRuleNode.PROP_NAME_predicate,
                "决策树分支条件存储在 predicate 属性");
        assertEquals("outputs", NopRuleNode.PROP_NAME_outputs);
        assertEquals("sortNo", NopRuleNode.PROP_NAME_sortNo);
        assertEquals("parent", NopRuleNode.PROP_NAME_parent);
        assertEquals("children", NopRuleNode.PROP_NAME_children);
    }
}
