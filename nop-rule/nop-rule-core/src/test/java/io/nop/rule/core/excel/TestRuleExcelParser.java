package io.nop.rule.core.excel;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.unittest.BaseTestCase;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.execute.RuleManager;
import io.nop.rule.core.model.RuleModel;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRuleExcelParser extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParseDecisionTree() {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/rule/test/test-table.rule.xlsx");
        RuleModel ruleModel = new RuleExcelModelParser().parseFromResource(resource);
        XNode node = DslModelHelper.dslModelToXNode(RuleConstants.XDSL_SCHEMA_RULE, ruleModel);
        node.dump();
    }

    @Test
    public void testExecuteDecisionTree() {
        IRuleManager ruleManager = getRuleManager();
        IRuleRuntime ruleRt = ruleManager.newRuntime();
        ruleRt.setInput("season", "Winter");
        ruleRt.setInput("guestCount", 4);
        Map<String, Object> output = ruleManager.executeRule("test/test-table", null, ruleRt);
        System.out.println(JsonTool.serialize(ruleRt.getLogMessages(), true));
        assertEquals("Roastbeef", output.get("dish"));

        ruleRt = ruleManager.newRuntime();
        ruleRt.setInput("season", "Summer");
        ruleRt.setInput("guestCount", 4);
        output = ruleManager.executeRule("test/test-table", null, ruleRt);
        assertEquals("Light Salad and nice Steak", output.get("dish"));
    }

    @Test
    public void testExecuteDecisionMatrix() {
        IRuleManager ruleManager = getRuleManager();
        IRuleRuntime ruleRt = ruleManager.newRuntime();
        ruleRt.setCollectLogMessage(true);
        ruleRt.setInput("是否有房", true);
        ruleRt.setInput("是否已婚", "已婚");
        Map<String, Object> baseInfo = new HashMap<>();
        baseInfo.put("age", 25);
        baseInfo.put("gender", 1);

        ruleRt.setInput("baseInfo", baseInfo);

        Map<String, Object> output = ruleManager.executeRule("test/test-matrix", null, ruleRt);
        System.out.println(JsonTool.serialize(ruleRt.getLogMessages(), true));
        assertEquals(9, output.get("result"));

        baseInfo.put("age", 50);
        ruleRt.setInput("是否已婚", "未婚");
        ruleRt.clearOutputs();
        output = ruleManager.executeRule("test/test-matrix", null, ruleRt);
        assertEquals("A", output.get("type"));
        assertEquals(14, output.get("result"));
    }

    private IRuleManager getRuleManager() {
        return new RuleManager();
    }
}
