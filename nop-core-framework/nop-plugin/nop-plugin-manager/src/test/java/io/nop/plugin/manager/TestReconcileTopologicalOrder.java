package io.nop.plugin.manager;

import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.manager.impl.VfsPluginDefinition;
import io.nop.plugin.test.OrderRecorder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2 reconcile 拓扑序 + 时间静止 focused tests（01 §三不变量 / §五时间静止语义）：
 * <ul>
 *     <li>批量激活正拓扑序：依赖链 A←B（B requires A）单 pass 内 A 先于 B ACTIVATED。</li>
 *     <li>批量去激活逆拓扑序：条件整体失效时消费者 B 先于提供者 A 退出（级联闭包）。</li>
 *     <li>混合批次：同一 reconcile pass 先去激活组（逆拓扑序）后激活组（正拓扑序）。</li>
 *     <li>requires 求值 = 依赖定义 ACTIVATED（依赖仅 LOADED 时门控不满足）。</li>
 *     <li>时间静止窗口 (a)：activate 展开期间条件失效 → 完成后收敛去激活（不中途打断）。</li>
 *     <li>时间静止窗口 (b)：deactivate 展开期间条件恢复 → 回退完成后重激活。</li>
 * </ul>
 *
 * <p>顺序断言机制：探针 activator（{@code OrderRecorderActivator} 等）在激活/effect 回退时向
 * 静态 {@link OrderRecorder} 记录事件，断言事件的相对顺序（比状态观测更强的顺序证明）。
 */
public class TestReconcileTopologicalOrder {

    private static final String PROVIDER_ID = "/nop/plugin/test/order-provider.plugin.xml";
    private static final String CONSUMER_ID = "/nop/plugin/test/order-consumer.plugin.xml";
    private static final String INDEPENDENT_ID = "/nop/plugin/test/order-independent.plugin.xml";
    private static final String FLIP_ID = "/nop/plugin/test/order-flip.plugin.xml";
    private static final String RECOVER_ID = "/nop/plugin/test/order-recover.plugin.xml";

    private static final String ORDER_GATE = "order.gate";
    private static final String ORDER_INDEPENDENT = "order.independent";

    private PluginManagerImpl manager;
    private Object priorOrderGate;
    private Object priorOrderIndependent;
    private Object priorOrderFlip;
    private Object priorOrderRecover;

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
        OrderRecorder.reset();
        priorOrderGate = capture(ORDER_GATE);
        priorOrderIndependent = capture(ORDER_INDEPENDENT);
        priorOrderFlip = capture("order.flip");
        priorOrderRecover = capture("order.recover");
        set(ORDER_GATE, "true");
        set(ORDER_INDEPENDENT, "false");
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
        restore(ORDER_GATE, priorOrderGate);
        restore(ORDER_INDEPENDENT, priorOrderIndependent);
        restore("order.flip", priorOrderFlip);
        restore("order.recover", priorOrderRecover);
    }

    private static Object capture(String key) {
        return AppConfig.getConfigProvider().getConfigValue(key, null);
    }

    private static void set(String key, String value) {
        AppConfig.getConfigProvider().assignConfigValue(key, value);
    }

    private static void restore(String key, Object value) {
        AppConfig.getConfigProvider().assignConfigValue(key, value);
    }

    /**
     * 批量激活正拓扑序：先 load consumer（requires 未满足保持 LOADED），再 load provider——
     * 随后的单次 reconcile 中 provider 先激活、consumer 后激活（依赖提供者在前）。
     */
    @Test
    public void testBatchActivationForwardTopologicalOrder() {
        IPlugin consumer = manager.loadPlugin(CONSUMER_ID);
        assertEquals(PluginState.LOADED, consumer.getState(), "requires 未满足：consumer 保持 LOADED");
        assertTrue(OrderRecorder.events().isEmpty(), "无激活事件");

        IPlugin provider = manager.loadPlugin(PROVIDER_ID);
        assertEquals(PluginState.ACTIVATED, provider.getState());
        assertEquals(PluginState.ACTIVATED, consumer.getState(), "级联激活：单 pass 内依赖链整体激活");
        assertEquals(List.of("activate:provider", "activate:consumer"), OrderRecorder.events(),
                "批量激活正拓扑序：provider（提供者）先于 consumer（消费者）ACTIVATED");
    }

    /**
     * 批量去激活逆拓扑序：依赖链整体激活后单键失效（if-property 同键 + requires 级联闭包）→
     * 一次 reconcile 中 consumer（消费者）先退出、provider（提供者）后退出。
     */
    @Test
    public void testBatchDeactivationReverseTopologicalOrder() {
        manager.loadPlugin(PROVIDER_ID);
        IPlugin consumer = manager.loadPlugin(CONSUMER_ID);
        IPlugin provider = manager.getPlugin(PROVIDER_ID);
        assertEquals(PluginState.ACTIVATED, provider.getState());
        assertEquals(PluginState.ACTIVATED, consumer.getState());

        // 条件整体失效（同一 if-property 键）→ 去激活组 = {provider, consumer}（级联闭包：
        // provider 门控失败 → consumer 的 requires 腿在假设态下也失败）
        OrderRecorder.reset();
        set(ORDER_GATE, "false");
        manager.reconcilePlugins();

        assertEquals(PluginState.LOADED, provider.getState());
        assertEquals(PluginState.LOADED, consumer.getState());
        assertEquals(List.of("deactivate:consumer", "deactivate:provider"), OrderRecorder.events(),
                "批量去激活逆拓扑序：消费者先于提供者退出（01 §三不变量）");
    }

    /**
     * 混合批次（同一 reconcile pass）：A←B 去激活组与独立插件 C 激活组并存——先处理去激活组
     * （逆拓扑序）再处理激活组（正拓扑序），顺序可观察。
     */
    @Test
    public void testMixedBatchDeactivationGroupProcessedBeforeActivationGroup() {
        manager.loadPlugin(PROVIDER_ID);
        IPlugin consumer = manager.loadPlugin(CONSUMER_ID);
        IPlugin independent = manager.loadPlugin(INDEPENDENT_ID);
        assertEquals(PluginState.ACTIVATED, consumer.getState());
        assertEquals(PluginState.LOADED, independent.getState(), "order.independent=false：保持 LOADED");

        // 单次 pass 前同时翻转两组条件：去激活组 {consumer, provider} + 激活组 {independent}
        OrderRecorder.reset();
        set(ORDER_GATE, "false");
        set(ORDER_INDEPENDENT, "true");
        manager.reconcilePlugins();

        assertEquals(PluginState.LOADED, consumer.getState());
        assertEquals(PluginState.LOADED, manager.getPlugin(PROVIDER_ID).getState());
        assertEquals(PluginState.ACTIVATED, independent.getState());
        assertEquals(List.of("deactivate:consumer", "deactivate:provider", "activate:independent"),
                OrderRecorder.events(),
                "混合批次裁定：同一 pass 先去激活组（逆拓扑序）后激活组（正拓扑序）");
    }

    /**
     * requires 求值 = 依赖定义 ACTIVATED（非 LOADED）：依赖已加载但门控关闭（仅 LOADED）时
     * 消费者门控不满足——reconcile 不激活、显式 activatePlugin 返回 false；门控打开后
     * reconcile 级联激活（provider 先于 consumer）。
     */
    @Test
    public void testRequiresEvaluatesDependencyActivationNotLoaded() {
        set(ORDER_GATE, "false");
        IPlugin provider = manager.loadPlugin(PROVIDER_ID);
        IPlugin consumer = manager.loadPlugin(CONSUMER_ID);
        assertEquals(PluginState.LOADED, provider.getState(), "门控关闭：provider 仅 LOADED");
        assertEquals(PluginState.LOADED, consumer.getState(), "requires 求值 = 依赖 ACTIVATED：依赖仅 LOADED 不满足");

        assertFalse(manager.activatePlugin(CONSUMER_ID), "门控未满足：activatePlugin no-op 返回 false");
        assertTrue(OrderRecorder.events().isEmpty(), "无激活发生");

        manager.reconcilePlugins();
        assertEquals(PluginState.LOADED, consumer.getState(), "reconcile 不激活（依赖未 ACTIVATED）");

        // 门控打开 → reconcile：provider 先激活（正拓扑序）→ consumer requires 满足 → 级联激活
        OrderRecorder.reset();
        set(ORDER_GATE, "true");
        manager.reconcilePlugins();
        assertEquals(PluginState.ACTIVATED, provider.getState());
        assertEquals(PluginState.ACTIVATED, consumer.getState());
        assertEquals(List.of("activate:provider", "activate:consumer"), OrderRecorder.events());
    }

    /**
     * 时间静止窗口 (a)：activate 展开期间条件失效（activator 内部翻转门控配置）→ 本次激活
     * 不中途打断、正常完成（Disposable 返回），完成后同一 reconcile 收敛去激活（最终 LOADED）。
     */
    @Test
    public void testTimeStaticActivateCompletesThenConvergesToDeactivated() {
        set("order.flip", "true");
        VfsPluginDefinition flip = (VfsPluginDefinition) manager.loadPlugin(FLIP_ID);

        // loadPlugin 触发的 reconcile：激活（展开期间 order.flip 被翻为 false）→ 完成 →
        // 不动点迭代收敛去激活（最终态 LOADED）
        assertEquals(PluginState.LOADED, flip.getState(), "激活完成后收敛去激活（不中途打断）");
        assertEquals(List.of("activate:flip", "deactivate:flip"), OrderRecorder.events(),
                "激活完整展开（activate 事件）→ 完成后收敛（deactivate 事件），无中间打断");

        // 门控仍关闭：后续 reconcile 不再变化
        manager.reconcilePlugins();
        assertEquals(PluginState.LOADED, flip.getState());
        assertEquals(2, OrderRecorder.events().size());
    }

    /**
     * 时间静止窗口 (b)：deactivate 展开期间条件恢复（effect 回退内部恢复门控配置）→ 回退
     * 正常完成，同一 reconcile 内重激活（最终态 ACTIVATED）。
     */
    @Test
    public void testTimeStaticDeactivateCompletesThenReactivates() {
        set("order.recover", "false");
        VfsPluginDefinition recover = (VfsPluginDefinition) manager.loadPlugin(RECOVER_ID);
        assertEquals(PluginState.LOADED, recover.getState(), "门控关闭：保持 LOADED");

        // 门控打开 → 激活（effect 注册：回退时恢复门控配置）
        OrderRecorder.reset();
        set("order.recover", "true");
        manager.reconcilePlugins();
        assertEquals(PluginState.ACTIVATED, recover.getState());
        assertEquals(List.of("activate:recover"), OrderRecorder.events());

        // 门控关闭 → 去激活展开期间 effect 回退恢复配置为 true → 回退完成后同一 pass 重激活
        set("order.recover", "false");
        manager.reconcilePlugins();
        assertEquals(PluginState.ACTIVATED, recover.getState(), "回退完成后重激活（最终态 ACTIVATED）");
        assertEquals(List.of("activate:recover", "deactivate:recover", "activate:recover"),
                OrderRecorder.events(), "完整回退（deactivate 事件）→ 重激活（activate 事件）");
    }
}
