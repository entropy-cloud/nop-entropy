package io.nop.cluster.sentinel.config;

import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C8-007（plan 2559 裁定 I）：SentinelRuleConfig 规则热更新对称化载体测试。
 * <p>
 * ① 空规则集 = 显式清空该族（对称热更新）：修复前 isEmpty→skip 形成单向棘轮，
 * 配置清空后旧规则残留至重启。
 * ② 解析失败 = 保留旧集 + 结构化 WARN + 失败计数器递增（裁定 I 的可观测承载）。
 * <p>
 * 装配形态钉定：sentinel-defaults.beans.xml 以 ioc:refresh-config-method="refreshConfig"
 * 挂接热更新——测试同时反射校验该装配未被移除。
 */
public class TestSentinelRuleRefresh {

    private final SentinelRuleConfig config = new SentinelRuleConfig();

    private static final String FLOW_RULE_JSON =
            "[{\"resource\":\"res-refresh-flow\",\"grade\":1,\"count\":100}]";
    private static final String DEGRADE_RULE_JSON =
            "[{\"resource\":\"res-refresh-degrade\",\"grade\":0,\"count\":2,\"timeWindow\":10}]";
    private static final String SYS_RULE_JSON =
            "[{\"highestSystemLoad\":5.0}]";
    private static final String AUTH_RULE_JSON =
            "[{\"resource\":\"res-refresh-auth\",\"limitApp\":\"app-a\",\"strategy\":0}]";

    @AfterEach
    void cleanup() {
        FlowRuleManager.loadRules(Collections.emptyList());
        DegradeRuleManager.loadRules(Collections.emptyList());
        SystemRuleManager.loadRules(Collections.emptyList());
        AuthorityRuleManager.loadRules(Collections.emptyList());
    }

    /** 常规路径：非空配置解析成功后规则生效。 */
    @Test
    public void testNonEmptyRulesLoad() {
        config.setFlowRules(FLOW_RULE_JSON);
        config.setDegradeRules(DEGRADE_RULE_JSON);
        config.setSysRules(SYS_RULE_JSON);
        config.setAuthRules(AUTH_RULE_JSON);
        config.refreshConfig();

        assertEquals(1, FlowRuleManager.getRules().size());
        assertEquals("res-refresh-flow", FlowRuleManager.getRules().get(0).getResource());
        assertEquals(1, DegradeRuleManager.getRules().size());
        assertEquals(1, AuthorityRuleManager.getRules().size());
        assertTrue(SystemRuleManager.getRules().size() >= 1);
        assertEquals(0, config.getFlowRuleLoadFailures());
    }

    /** 裁定 I ①：空规则集 = 显式清空该族（对称热更新，去单向棘轮）。 */
    @Test
    public void testEmptyRuleSetClearsFamily() {
        config.setFlowRules(FLOW_RULE_JSON);
        config.setDegradeRules(DEGRADE_RULE_JSON);
        config.setSysRules(SYS_RULE_JSON);
        config.setAuthRules(AUTH_RULE_JSON);
        config.refreshConfig();
        assertEquals(1, FlowRuleManager.getRules().size());

        // 热更新为空——四族全部显式清空（修复前 isEmpty→skip 使旧规则残留）
        config.setFlowRules(null);
        config.setDegradeRules("");
        config.setSysRules(null);
        config.setAuthRules(null);
        config.refreshConfig();

        assertTrue(FlowRuleManager.getRules().isEmpty(),
                "empty flow config must clear the flow rule family (symmetric hot-update)");
        assertTrue(DegradeRuleManager.getRules().isEmpty(),
                "empty degrade config must clear the degrade rule family");
        assertTrue(SystemRuleManager.getRules().isEmpty(),
                "empty sys config must clear the sys rule family");
        assertTrue(AuthorityRuleManager.getRules().isEmpty(),
                "empty auth config must clear the auth rule family");
    }

    /** 裁定 I ②：解析失败 = 保留旧集 + WARN 失败计数递增（可观测承载）。 */
    @Test
    public void testParseFailureRetainsOldSetAndCountsFailure() {
        config.setFlowRules(FLOW_RULE_JSON);
        config.refreshConfig();
        List<FlowRule> oldRules = FlowRuleManager.getRules();
        assertEquals(1, oldRules.size());
        assertEquals(0, config.getFlowRuleLoadFailures());

        config.setFlowRules("{not-valid-json!!");
        config.refreshConfig();

        // 旧集保留生效（fail-safe：解析失败不得清空在役规则）
        assertEquals(oldRules, FlowRuleManager.getRules(),
                "parse failure must retain the previously loaded rule set");
        // 失败计数器递增——本裁定的可观测载体
        assertEquals(1, config.getFlowRuleLoadFailures(),
                "parse failure must increment the WARN failure counter");

        // 再次失败计数继续递增
        config.refreshConfig();
        assertEquals(2, config.getFlowRuleLoadFailures());
        assertEquals(oldRules, FlowRuleManager.getRules());
    }

    /** 解析失败只隔离失效族，其余族正常更新。 */
    @Test
    public void testParseFailureIsolatedPerFamily() {
        config.setFlowRules("{broken");
        config.setDegradeRules(DEGRADE_RULE_JSON);
        config.refreshConfig();

        assertEquals(1, config.getFlowRuleLoadFailures());
        assertEquals(0, config.getDegradeRuleLoadFailures());
        assertEquals(1, DegradeRuleManager.getRules().size(),
                "healthy family must still refresh while a sibling family fails to parse");
    }

    /** 装配形态钉定：sentinel-defaults.beans.xml 的 refresh-config-method 挂接不被移除。 */
    @Test
    public void testBeansWiringKeepsRefreshConfigHook() throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("_vfs/nop/cluster/beans/sentinel-defaults.beans.xml")) {
            assertNotNull(in, "sentinel-defaults.beans.xml must exist on _vfs classpath");
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("io.nop.cluster.sentinel.config.SentinelRuleConfig"));
            assertTrue(xml.contains("ioc:refresh-config-method=\"refreshConfig\""),
                    "hot-update wiring via refreshConfig must not be removed");
            assertTrue(xml.contains("ioc:config-prefix=\"nop.cluster.sentinel\""));
        }
    }
}
