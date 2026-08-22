package io.nop.plugin.manager;

import io.nop.api.core.config.SimpleConfigProvider;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R2 宽松比较语义保留测试（原独立 Helper 比较用例迁移至保留路径）：宽松比较逻辑收敛到
 * {@code PluginManagerImpl.CoeffectEvaluatorImpl.isGlobalConfigMatched} 实现位置
 * （configValueMatches），本测试经 manager → reconcile → 定义级 if-property 门控激活全链
 * 验证 Boolean/Number/String 等价比较语义不丢弃（W5 裁定）。
 */
public class TestCoeffectConfigMatching {

    private static final String LOOSE_NUMBER_ID = "/nop/plugin/test/loose-number.plugin.xml";
    private static final String LOOSE_STRING_ID = "/nop/plugin/test/loose-string.plugin.xml";
    private static final String LOOSE_BOOL_ID = "/nop/plugin/test/loose-bool.plugin.xml";

    private PluginManagerImpl manager;
    private SimpleConfigProvider provider;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        manager = new PluginManagerImpl();
        provider = new SimpleConfigProvider();
        manager.setGlobalConfigProvider(provider);
    }

    @AfterEach
    public void tearDown() {
        for (IPlugin plugin : manager.getLoadedPlugins()) {
            try {
                plugin.deactivate();
            } catch (RuntimeException ignore) {
                // Intentionally ignored: tearDown must proceed
            }
            try {
                plugin.unload();
            } catch (RuntimeException ignore) {
                // Intentionally ignored: cleanup tolerance
            }
        }
    }

    /**
     * 设置全局配置值并观察门控结果（null = 不设置该键）：每个用例使用全新 provider（避免
     * SimpleConfigReference 首次赋值钉死类型导致跨用例类型转换失败）→ loadPlugin（幂等，
     * 已加载时返回既有定义）→ reconcilePlugins 重评估门控 → 返回定义最终态。全程经保留的
     * isGlobalConfigMatched 比较路径（reconcile → isDefinitionGateSatisfied →
     * evaluator.isGlobalConfigMatched）。
     */
    private PluginState gateOutcomeRaw(String pluginId, Object configValue) {
        provider = new SimpleConfigProvider();
        manager.setGlobalConfigProvider(provider);
        String propName = pluginId.equals(LOOSE_NUMBER_ID) ? "loose.num"
                : pluginId.equals(LOOSE_STRING_ID) ? "loose.env" : "loose.bool";
        if (configValue != null) {
            provider.assignConfigValue(propName, configValue);
        }
        manager.loadPlugin(pluginId);
        manager.reconcilePlugins();
        return manager.getPlugin(pluginId).getState();
    }

    /**
     * 数值宽松等价（expected spec 字符串 "10"）：Number 与数值字符串等价比较——
     * Integer 10 / Double 10.0 / String "10" 均匹配；11 / "10.5" / "abc" / null 不匹配。
     */
    @Test
    public void testNumericLooseEquivalence() {
        assertEquals(PluginState.ACTIVATED, gateOutcomeRaw(LOOSE_NUMBER_ID, 10), "Integer 10 与 \"10\" 等价");
        assertEquals(PluginState.ACTIVATED, gateOutcomeRaw(LOOSE_NUMBER_ID, 10.0), "Double 10.0 与 \"10\" 等价");
        assertEquals(PluginState.ACTIVATED, gateOutcomeRaw(LOOSE_NUMBER_ID, "10"), "String \"10\" 直接匹配");

        assertEquals(PluginState.LOADED, gateOutcomeRaw(LOOSE_NUMBER_ID, 11), "数值不等不匹配");
        assertEquals(PluginState.LOADED, gateOutcomeRaw(LOOSE_NUMBER_ID, "10.5"), "数值字符串不等不匹配");
        assertEquals(PluginState.LOADED, gateOutcomeRaw(LOOSE_NUMBER_ID, "abc"), "非数值字符串不匹配");
        assertEquals(PluginState.LOADED, gateOutcomeRaw(LOOSE_NUMBER_ID, null), "actual null 一律不匹配");
    }

    /**
     * 字符串精确比较（expected spec 字符串 "dev"）：相等匹配、不等不匹配。
     */
    @Test
    public void testStringEquivalence() {
        assertEquals(PluginState.ACTIVATED, gateOutcomeRaw(LOOSE_STRING_ID, "dev"));
        assertEquals(PluginState.LOADED, gateOutcomeRaw(LOOSE_STRING_ID, "prod"));
    }

    /**
     * 布尔宽松等价（expected 缺省 Boolean.TRUE）：Boolean.TRUE / String "true" 均匹配；
     * Boolean.FALSE / String "false" 不匹配。
     */
    @Test
    public void testBooleanLooseEquivalence() {
        assertEquals(PluginState.ACTIVATED, gateOutcomeRaw(LOOSE_BOOL_ID, Boolean.TRUE),
                "Boolean.TRUE 与缺省 true 等价");
        assertEquals(PluginState.ACTIVATED, gateOutcomeRaw(LOOSE_BOOL_ID, "true"),
                "String \"true\" 与 Boolean.TRUE 等价（parseBoolean）");
        assertEquals(PluginState.LOADED, gateOutcomeRaw(LOOSE_BOOL_ID, Boolean.FALSE));
        assertEquals(PluginState.LOADED, gateOutcomeRaw(LOOSE_BOOL_ID, "false"));
    }
}
