package io.nop.rule.api.beans;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.VarMetaBean;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构性契约测试：nop-rule-api 的请求/结果/元数据/日志 bean 为 codegen 生成的数据类，
 * 模块内零实现逻辑。这里固化规则引擎对外数据契约：规则身份三元组（name+version）、
 * 输入/输出变量集合、ruleMatch 布尔结果与日志消息结构。
 */
public class TestRuleApiBeanContracts {

    @Test
    public void testRuleKeyBeanCarriesRuleIdentity() {
        RuleKeyBean key = new RuleKeyBean("billing.discount");
        assertEquals("billing.discount", key.getRuleName(), "构造器必须初始化规则名");
        key.setRuleVersion(7L);
        assertEquals(7L, key.getRuleVersion());
        assertEquals("billing.discount", key.getRuleName());
    }

    @Test
    public void testRuleRequestBeanInputsRoundTrip() {
        RuleRequestBean request = new RuleRequestBean();
        request.setRuleName("billing.discount");
        request.setRuleVersion(2L);
        request.setInputs(Map.of("guestCount", 3, "season", "Fall"));

        assertEquals("billing.discount", request.getRuleName());
        assertEquals(2L, request.getRuleVersion());
        assertEquals(3, request.getInputs().get("guestCount"));
        assertEquals("Fall", request.getInputs().get("season"));
    }

    @Test
    public void testRuleResultBeanMatchOutputsAndLogs() {
        RuleResultBean result = new RuleResultBean();
        assertTrue(!result.getRuleMatch(), "未赋值时 ruleMatch 基本布尔必须为 false");
        result.setRuleName("billing.discount");
        result.setRuleVersion(2L);
        result.setRuleMatch(true);
        result.setOutputs(Map.of("dish", "Spareribs"));
        result.setLogMessages(List.of(new RuleLogMessageBean()));

        assertEquals("billing.discount", result.getRuleName());
        assertEquals(2L, result.getRuleVersion());
        assertTrue(result.getRuleMatch(), "命中规则时 ruleMatch 必须为 true");
        assertEquals("Spareribs", result.getOutputs().get("dish"));
        assertEquals(1, result.getLogMessages().size());
    }

    @Test
    public void testRuleMetaBeanDeclaresInputOutputVariables() {
        RuleMetaBean meta = new RuleMetaBean();
        VarMetaBean input = new VarMetaBean();
        input.setName("season");
        VarMetaBean output = new VarMetaBean();
        output.setName("dish");
        meta.setRuleName("billing.discount");
        meta.setDisplayName("折扣规则");
        meta.setInputs(List.of(input));
        meta.setOutputs(List.of(output));

        assertEquals("billing.discount", meta.getRuleName());
        assertEquals("折扣规则", meta.getDisplayName());
        assertEquals("season", meta.getInputs().get(0).getName());
        assertEquals("dish", meta.getOutputs().get(0).getName());
    }

    @Test
    public void testRuleLogMessageBeanRoundTrip() {
        RuleLogMessageBean message = new RuleLogMessageBean();
        Timestamp logTime = new Timestamp(1700000000000L);
        message.setLogTime(logTime);
        message.setMessage("node hit");
        message.setRuleNodeId("node-1");
        message.setRuleNodeLabel("季节判断");
        message.setContext(Map.of("season", "Fall"));

        assertEquals(logTime, message.getLogTime());
        assertEquals("node hit", message.getMessage());
        assertEquals("node-1", message.getRuleNodeId());
        assertEquals("季节判断", message.getRuleNodeLabel());
        assertEquals("Fall", message.getContext().get("season"));
    }

    @Test
    public void testRequestAndResultAndLogBeansAreDataBeans() {
        assertNotNull(RuleRequestBean.class.getAnnotation(DataBean.class));
        assertNotNull(RuleResultBean.class.getAnnotation(DataBean.class));
        assertNotNull(RuleLogMessageBean.class.getAnnotation(DataBean.class));
    }
}
