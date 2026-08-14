package io.nop.plugin.manager;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.InstanceState;
import io.nop.plugin.manager.impl.PluginInstanceImpl;
import io.nop.plugin.manager.impl.PluginManagerImpl;
import io.nop.plugin.manager.impl.VfsPluginDefinition;
import io.nop.plugin.test.IConfigReader;
import io.nop.plugin.test.ITool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVE_CHILDREN_EXIST;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_EXISTS;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_PARENT_CHAIN_CYCLE;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_PARENT_NOT_ACTIVATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6 Phase 1 + Phase 4：parent 层级实例化 + P2-D 裁决（Plan-2026-08-14-2007-3）。
 * <ul>
 *     <li>子容器 parent 链接线：服务查找沿链回退（跨定义 parent 链：parent-tools → child-tools，
 *     两级链回退）——子容器无 bean T、父容器有，子 getService 命中。</li>
 *     <li>配置层叠：父合并视图 + 定义默认 + 子实例配置（子覆盖父、父独有键继承）。</li>
 *     <li>父配置热应用传播：父定义 updateConfig → 父 + 子 + 孙合并视图与 bean 属性递归刷新
 *     （传播路径钉死，防空心接线——只改父而子持陈旧快照即失败）。</li>
 *     <li>级联 destroy：destroy 父 → 子（及孙，跨定义）实例从 registry 移除（manager 级
 *     全局映射驱动，先子后父）。</li>
 *     <li>父 DEACTIVATED 时 createInstance(parent) 抛明确异常；父链重复 (pluginId, instanceKey)
 *     对（链腐化/环引用）抛明确异常。</li>
 *     <li>P2-D 显式路径守卫：有 ACTIVATED 子实例时父 deactivate 抛明确异常（先处理子再处理父）。</li>
 *     <li>P2-D reconcile 路径级联：reconcile 决定去激活父实例 → 先级联去激活 ACTIVATED 子实例
 *     （保留实例对象）再父（子服务回退不触发 ERR_IOC_CONTAINER_NOT_STARTED 硬失败）；
 *     父 DEACTIVATED 时子实例不激活（父链抑制激活，无 reconcile 冲突）。</li>
 *     <li>createInstance 检查顺序：父状态检查先于门控（父 DEACTIVATED 显式抛错不返回 null）；
 *     重复 key 检查先于父状态检查（重复 key 异常优先）。</li>
 * </ul>
 */
public class TestParentHierarchy {

    private static final String PARENT_ID = "/nop/plugin/test/parent-tools.plugin.xml";
    private static final String PARENT_GATED_ID = "/nop/plugin/test/parent-gated.plugin.xml";
    private static final String CHILD_ID = "/nop/plugin/test/child-tools.plugin.xml";
    private static final String GATE_CLOSED_ID = "/nop/plugin/test/gate-closed.plugin.xml";
    private static final String GLOBAL_TOOLS_ENABLED = "agent.tools.enabled";
    private static final String PARENT_ENABLED = "agent.parent.enabled";

    private PluginManagerImpl manager;
    private Object priorToolsEnabled;
    private Object priorParentEnabled;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        AppConfig.getConfigProvider().assignConfigValue("agent.timeout", "global-timeout");
        AppConfig.getConfigProvider().assignConfigValue("test.plugin.global.var", "global-default");
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        manager = new PluginManagerImpl();
        manager.loadPlugin(PARENT_ID);
        manager.loadPlugin(CHILD_ID);
        priorToolsEnabled = AppConfig.getConfigProvider().getConfigValue(GLOBAL_TOOLS_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, "true");
        priorParentEnabled = AppConfig.getConfigProvider().getConfigValue(PARENT_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(PARENT_ENABLED, "true");
    }

    @AfterEach
    public void tearDown() {
        for (IPlugin plugin : manager.getLoadedPlugins()) {
            for (IPluginInstance instance : plugin.getInstances()) {
                try {
                    instance.destroy();
                } catch (RuntimeException ignore) {
                    // Intentionally ignored: tearDown must proceed even if a deactivated instance rejects destroy
                }
            }
        }
        AppConfig.getConfigProvider().assignConfigValue(GLOBAL_TOOLS_ENABLED, priorToolsEnabled);
        AppConfig.getConfigProvider().assignConfigValue(PARENT_ENABLED, priorParentEnabled);
    }

    /**
     * 子容器 parent 链接线：子容器无 bean T、父容器有 → 子 getService 沿链回退命中父实例服务
     * （跨定义 parent 链 + 两级回退）；子本地 bean 不被回退遮蔽。
     */
    @Test
    public void testServiceFallbackWalksParentChain() {
        IPluginInstance p1 = manager.createInstance(PARENT_ID, "p1", Map.of(), null);
        IPluginInstance c1 = manager.createInstance(CHILD_ID, "c1", Map.of(), p1);
        IPluginInstance g1 = manager.createInstance(CHILD_ID, "g1", Map.of(), c1);

        assertNull(p1.getParent(), "顶层实例 parent 保持 null");
        assertEquals(p1, c1.getParent());
        assertEquals(c1, g1.getParent());

        // 子容器（child-tools 无 ITool bean）未命中 → 回退父容器 → parent-tools 的 BashTool（primary）
        assertEquals("bash", c1.getService(ITool.class).getName(), "跨定义服务回退（子 → 父）");
        assertEquals("bash", g1.getService(ITool.class).getName(), "沿链两级回退（孙 → 子 → 父）");

        // 子本地 bean 正常解析（不受回退影响）
        assertEquals("global-timeout", c1.getService(IConfigReader.class).getTimeout());

        // 集合版同款回退：父容器全部 ITool 候选可见
        Collection<ITool> tools = g1.getServices(ITool.class);
        assertEquals(2, tools.size(), "getServices 沿链回退父容器候选");
    }

    /**
     * 配置层叠：父合并视图 + 定义默认 + 子实例配置（子覆盖父；父独有键继承）。
     */
    @Test
    public void testConfigLayeringChildOverridesParent() {
        IPluginInstance p1 = manager.createInstance(PARENT_ID, "p1",
                Map.of("agent.timeout", "10", "agent.only-instance", "from-parent"), null);
        IPluginInstance c1 = manager.createInstance(CHILD_ID, "c1", Map.of("agent.timeout", "20"), p1);

        assertEquals("20", c1.getConfig().get("agent.timeout"), "子实例配置覆盖父值");
        assertEquals("from-parent", c1.getConfig().get("agent.only-instance"), "父独有键继承进子合并视图");
        assertEquals("20", c1.getService(IConfigReader.class).getTimeout(), "子 bean 经合并视图读到覆盖值");
        assertEquals("from-parent", c1.getService(IConfigReader.class).getOnlyInstance(), "子 bean 读到父独有键");

        // 父合并视图不含子的覆盖（层叠只向下一方向流动）
        assertEquals("10", p1.getConfig().get("agent.timeout"));
        assertEquals("from-parent", p1.getConfig().get("agent.only-instance"), "父自身实例配置仍在父视图");
    }

    /**
     * 父配置热应用传播（传播路径钉死，防空心接线）：父定义 updateConfig → 父实例重算合并视图 →
     * 递归调用子/孙的同名重算 → 子合并视图 + 子 bean 属性真实重绑定（非只改父而子持陈旧快照）。
     */
    @Test
    public void testParentHotApplyPropagationToChildren() {
        IPlugin parentDef = manager.loadPlugin(PARENT_ID);
        IPluginInstance p1 = manager.createInstance(PARENT_ID, "p1", Map.of(), null);
        IPluginInstance c1 = manager.createInstance(CHILD_ID, "c1", Map.of(), p1);
        IPluginInstance g1 = manager.createInstance(CHILD_ID, "g1", Map.of(), c1);

        parentDef.updateConfig(Map.of("agent.mode", "dev"));
        assertEquals("dev", p1.getConfig().get("agent.mode"), "父自身热应用");
        assertEquals("dev", c1.getConfig().get("agent.mode"), "父热应用必须传播到子（合并视图）");
        assertEquals("dev", g1.getConfig().get("agent.mode"), "父热应用必须递归传播到孙（合并视图）");
        assertEquals("dev", c1.getService(IConfigReader.class).getMode(), "子实例 bean 响应式重绑定");
        assertEquals("dev", g1.getService(IConfigReader.class).getMode(), "孙实例 bean 响应式重绑定");

        // 二次更新：传播路径可持续（非一次性接线）
        parentDef.updateConfig(Map.of("agent.mode", "prod"));
        assertEquals("prod", c1.getConfig().get("agent.mode"));
        assertEquals("prod", g1.getConfig().get("agent.mode"));
        assertEquals("prod", c1.getService(IConfigReader.class).getMode());
    }

    /**
     * 级联 destroy（manager 级全局映射驱动，先子后父，跨定义）：destroy 父 → 子及孙
     * 实例从各自定义 registry 移除；全局映射同步清理（无残留）。
     */
    @Test
    public void testCascadeDestroyParentDestroysDescendantsAcrossDefinitions() {
        IPluginInstance p1 = manager.createInstance(PARENT_ID, "p1", Map.of(), null);
        IPluginInstance c1 = manager.createInstance(CHILD_ID, "c1", Map.of(), p1);
        IPluginInstance g1 = manager.createInstance(CHILD_ID, "g1", Map.of(), c1);
        IPluginInstance c2 = manager.createInstance(CHILD_ID, "c2", Map.of(), p1);

        manager.destroyInstance(PARENT_ID, "p1");

        assertNull(manager.getInstance(PARENT_ID, "p1"));
        assertNull(manager.getInstance(CHILD_ID, "c1"), "子实例随父级联销毁");
        assertNull(manager.getInstance(CHILD_ID, "g1"), "孙实例随父级联销毁");
        assertNull(manager.getInstance(CHILD_ID, "c2"), "兄弟实例随父级联销毁");
        assertTrue(manager.getInstances(PARENT_ID).isEmpty());
        assertTrue(manager.getInstances(CHILD_ID).isEmpty());
    }

    /**
     * 父 DEACTIVATED 时 createInstance(parent) 抛明确异常（禁止挂到已停容器）；
     * 异常带实例 key 参数。
     */
    @Test
    public void testCreateInstanceWithDeactivatedParentThrows() {
        IPluginInstance p1 = manager.createInstance(PARENT_ID, "p1", Map.of(), null);
        FutureHelper.syncGet(p1.deactivate());
        assertEquals(InstanceState.DEACTIVATED, p1.getState());

        NopException e = assertThrows(NopException.class,
                () -> manager.createInstance(CHILD_ID, "c1", Map.of(), p1));
        assertEquals(ERR_PLUGIN_PARENT_NOT_ACTIVATED.getErrorCode(), e.getErrorCode());
        assertEquals("c1", e.getParam("instanceKey"));
    }

    /**
     * P2-D 显式 deactivate 守卫：父实例存在 ACTIVATED 子实例时 deactivate() 抛明确异常
     * （带子实例 key 参数；先处理子再处理父——与 unload 守卫同构）。
     */
    @Test
    public void testExplicitDeactivateGuardWithActiveChildren() {
        IPluginInstance p1 = manager.createInstance(PARENT_ID, "p1", Map.of(), null);
        IPluginInstance c1 = manager.createInstance(CHILD_ID, "c1", Map.of(), p1);

        NopException e = assertThrows(NopException.class,
                () -> FutureHelper.syncGet(p1.deactivate()));
        assertEquals(ERR_PLUGIN_ACTIVE_CHILDREN_EXIST.getErrorCode(), e.getErrorCode());
        assertEquals("p1", e.getParam("instanceKey"));
        assertTrue(e.getParam("instanceKeys").toString().contains("c1"), "守卫异常带子实例 key");

        // 先处理子（子先 deactivate）→ 父 deactivate 放行
        FutureHelper.syncGet(c1.deactivate());
        FutureHelper.syncGet(p1.deactivate());
        assertEquals(InstanceState.DEACTIVATED, p1.getState());
        assertEquals(InstanceState.DEACTIVATED, c1.getState());
    }

    /**
     * 父链环防护：父链上出现重复 (pluginId, instanceKey) 对（链腐化/环引用场景，
     * 如 reload 重建解析到陈旧同 key 实例）→ createInstance 抛明确异常。
     */
    @Test
    public void testParentChainCycleRejected() {
        VfsPluginDefinition defA = (VfsPluginDefinition) manager.loadPlugin(PARENT_ID);
        VfsPluginDefinition defB = (VfsPluginDefinition) manager.loadPlugin(CHILD_ID);
        // 手工种子链腐化：x1 与 x2 同为 (parent-tools, "dup") 对（x2.parent = x1）——
        // 经 addInstance 构造（createInstance 的重复 key 检查先于父检查，不能走公共入口）
        PluginInstanceImpl x1 = new PluginInstanceImpl(defA, "dup", Map.of(), null);
        defA.addInstance(x1);
        FutureHelper.syncGet(x1.activate());
        PluginInstanceImpl x2 = new PluginInstanceImpl(defA, "dup", Map.of(), x1);
        defA.addInstance(x2);
        FutureHelper.syncGet(x2.activate());

        NopException e = assertThrows(NopException.class,
                () -> manager.createInstance(CHILD_ID, "y", Map.of(), x2));
        assertEquals(ERR_PLUGIN_PARENT_CHAIN_CYCLE.getErrorCode(), e.getErrorCode(),
                "父链重复 (pluginId, instanceKey) 对必须显式失败（不静默挂靠）");

        x1.destroy();
        x2.destroy();
    }

    /**
     * P2-D reconcile 路径（W5 引擎驱动，跨 plan 契约）：reconcile 决定去激活父实例（定义级
     * if-property 关闭）→ 先级联去激活 ACTIVATED 子实例（保留实例对象）再父——子实例容器
     * parent 不指向已停容器，服务回退不触发 ERR_IOC_CONTAINER_NOT_STARTED 硬失败；父链
     * 抑制激活：父仍 DEACTIVATED 时子实例不重新激活（无"父停即子停、reconcile 又激活"冲突）。
     */
    @Test
    public void testReconcileParentDeactivationCascadesChildrenFirst() {
        manager.loadPlugin(PARENT_GATED_ID);
        IPluginInstance p1 = manager.createInstance(PARENT_GATED_ID, "p1", Map.of(), null);
        IPluginInstance c1 = manager.createInstance(CHILD_ID, "c1", Map.of(), p1);
        assertEquals(InstanceState.ACTIVATED, p1.getState());
        assertEquals(InstanceState.ACTIVATED, c1.getState());
        assertEquals("bash", c1.getService(ITool.class).getName(), "激活期服务沿链回退正常");

        // 关闭父定义级 if-property → reconcile 决定去激活父 → 先子后父级联
        AppConfig.getConfigProvider().assignConfigValue(PARENT_ENABLED, "false");
        manager.reconcileInstances();

        assertEquals(InstanceState.DEACTIVATED, c1.getState(), "子实例先级联去激活（先子后父）");
        assertEquals(InstanceState.DEACTIVATED, p1.getState(), "父实例再去激活");

        // 子实例已去激活 → getService 快速失败 ERR_PLUGIN_INACTIVE（非 ERR_IOC_CONTAINER_NOT_STARTED
        // 硬失败——若子仍 ACTIVATED 挂已停容器，服务回退调用会抛 IoC 容器错误）
        NopException e = assertThrows(NopException.class, () -> c1.getService(ITool.class));
        assertEquals(ERR_PLUGIN_INACTIVE.getErrorCode(), e.getErrorCode(),
                "级联去激活后子实例服务快速失败，不触发 ERR_IOC_CONTAINER_NOT_STARTED");

        // 父链抑制激活：父仍 DEACTIVATED、子条件仍满足 → reconcile 不重新激活子（无冲突循环）
        manager.reconcileInstances();
        assertEquals(InstanceState.DEACTIVATED, c1.getState(), "父链不健康时子实例不激活（抑制重激活）");

        // 父恢复 → 父先激活，子沿链恢复激活（服务回退恢复）
        AppConfig.getConfigProvider().assignConfigValue(PARENT_ENABLED, "true");
        manager.reconcileInstances();
        assertEquals(InstanceState.ACTIVATED, p1.getState());
        assertEquals(InstanceState.ACTIVATED, c1.getState(), "父恢复后子实例重新激活");
        assertEquals("bash", c1.getService(ITool.class).getName(), "父恢复后服务沿链回退恢复");
    }

    /**
     * createInstance 检查顺序（P2-D 裁定）：父状态检查先于门控——父 DEACTIVATED 是调用错误
     * 显式抛出（不返回 null）；门控是定义级条件（返回 null）。
     */
    @Test
    public void testParentStateCheckPrecedesGate() {
        manager.loadPlugin(GATE_CLOSED_ID);
        IPluginInstance p1 = manager.createInstance(PARENT_ID, "p1", Map.of(), null);
        FutureHelper.syncGet(p1.deactivate());

        // gate-closed 定义级 requires=missing-dep 不满足（门控关闭）——若父检查在门控后，
        // 此调用会返回 null；父检查先于门控 → 必须抛父异常
        NopException e = assertThrows(NopException.class,
                () -> manager.createInstance(GATE_CLOSED_ID, "g1", Map.of(), p1));
        assertEquals(ERR_PLUGIN_PARENT_NOT_ACTIVATED.getErrorCode(), e.getErrorCode(),
                "父状态检查先于定义级门控（错误优先于条件）");
    }

    /**
     * W6 移交项裁定（W7，watch-only residual 钉死）：父链服务查找总未命中（子容器 +
     * 父链沿途 + 顶层均无候选，顶层 parentContainer 为 null 不扩展宿主）→ 显式抛容器标准
     * 错误 ERR_IOC_UNKNOWN_BEAN_FOR_TYPE（错误码完整、带 beanType 参数）——不静默返回 null、
     * 不翻译错误码（透传）。观察实证：BeanContainerImpl.getBeanByType 未命中即抛该码。
     */
    @Test
    public void testParentChainTotalMissThrowsExplicitError() {
        IPluginInstance p1 = manager.createInstance(PARENT_ID, "p1", Map.of(), null);
        IPluginInstance c1 = manager.createInstance(CHILD_ID, "c1", Map.of(), p1);

        NopException e = assertThrows(NopException.class, () -> c1.getService(Callable.class));
        assertEquals(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_TYPE.getErrorCode(), e.getErrorCode(),
                "父链总未命中 → 容器标准显式错误（透传，不静默返回 null）");
        assertEquals(Callable.class.toString(), e.getParam("beanType"), "错误带 beanType 参数（容器标准格式）");
        assertNull(p1.getParent(), "顶层实例 parentContainer 为 null（不扩展宿主链）");
    }

    /**
     * createInstance 检查顺序边界（P2-D 裁定）：重复 key + DEACTIVATED 父 → 重复 key 异常优先
     * （checkLoaded → 重复 key → 父状态 → 门控 → 创建）。
     */
    @Test
    public void testDuplicateKeyCheckPrecedesParentState() {
        IPluginInstance p1 = manager.createInstance(PARENT_ID, "p1", Map.of(), null);
        manager.createInstance(PARENT_ID, "dup", Map.of(), null);
        FutureHelper.syncGet(p1.deactivate());

        // 同 key 已存在 + 父 DEACTIVATED：重复 key 检查先于父状态检查 → 重复 key 异常
        NopException e = assertThrows(NopException.class,
                () -> manager.createInstance(PARENT_ID, "dup", Map.of(), p1));
        assertEquals(ERR_PLUGIN_INSTANCE_EXISTS.getErrorCode(), e.getErrorCode(),
                "重复 key 检查先于父状态检查");
    }
}
