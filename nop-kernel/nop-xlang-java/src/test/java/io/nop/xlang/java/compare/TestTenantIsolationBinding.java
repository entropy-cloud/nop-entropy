package io.nop.xlang.java.compare;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.ICancellable;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.core.resource.tenant.ITenantResourceProvider;
import io.nop.core.resource.tenant.ResourceTenantManager;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.backend.EvalBackendDecision;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.backend.EvalBackendRouter;
import io.nop.xlang.backend.EvalStaticBoundExecutable;
import io.nop.xlang.backend.EvalStaticDegradedExecutable;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.gen.ExecutableTreeFingerprints;
import io.nop.xlang.java.gen.GeneratedClassManifest;
import io.nop.xlang.xpl.loader.XplModelLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.core.CoreConfigs.CFG_TENANT_RESOURCE_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户隔离绑定测试（I10 Phase 2，D5 裁定机制的真实构造——禁止"注入失配指纹 + 贴租户标签"模拟）：
 * 同路径两租户经真实机制产生不同树——(1) {@code CFG_TENANT_RESOURCE_ENABLED} 启用（VFS 租户分路
 * 与 RCM cache 创建两处活读）；(2) 测试资源路径落非 nop- 前缀模块段（{@code isSupportTenant}
 * 模块名回退）；(3) {@code ContextProvider.runWithTenant} 租户上下文；(4) 测试
 * {@code ITenantResourceProvider} 供给 {@code /_tenant/<id>/...} 覆盖资源经
 * {@code DeltaResourceStore} 租户前缀优先分路（覆盖资源以标准路径呈现——与生产租户存储一致）；
 * (5) 专用测试 modelType（其 RCM loading cache 在租户启用后创建 = per-tenant cache）。
 *
 * <p>断言：租户 A（基树）绑定命中生成类；租户 B（覆盖树）稳态降级（tenant-divergent-tree，
 * WARN 去重）+ 解释器执行租户语义；两租户模型实例隔离（RCM per-tenant cache）、互不串用。
 */
public class TestTenantIsolationBinding {

    private static final String PATH = TenantBindingTestUnit.PATH;

    private static final String FIXTURE_FQN = EvalMethodConvention.GENERATED_PACKAGE + '.'
            + EvalMethodConvention.generatedClassName(PATH);

    private static IConfigReference<Boolean> tenantSwitch;

    private static Boolean tenantSwitchBaseline;

    private static ITenantResourceProvider oldProvider;

    private static ICancellable modelConfigCancellable;

    private static InMemoryResourceStore inMemoryLayer;

    private static boolean createdInMemoryLayer;

    private static IResource baseResource;

    private static TenantOverrideStore tenantOverrideStore;

    /** 租户覆盖存储：{@code /_tenant/<id>/<path>} 查询 → 以标准路径呈现的覆盖资源 */
    static final class TenantOverrideStore implements IResourceStore {

        private final Map<String, String> overrides = new LinkedHashMap<>();

        private final String tenantId;

        TenantOverrideStore(String tenantId) {
            this.tenantId = tenantId;
        }

        void put(String canonicalPath, String source) {
            overrides.put(canonicalPath, source);
        }

        @Override
        public IResource getResource(String path, boolean returnNullIfNotExists) {
            // DeltaResourceStore 以 /_tenant/<id>/<path> 查询；返回以标准路径呈现的覆盖资源
            String canonical = ResourceTenantPathHelper.toCanonicalPath(tenantId, path);
            String source = canonical == null ? null : overrides.get(canonical);
            return source == null ? null : new InMemoryTextResource(canonical, source);
        }

        @Override
        public List<? extends IResource> getChildren(String path) {
            return Collections.emptyList();
        }

        @Override
        public boolean supportSave(String path) {
            return false;
        }

        @Override
        public String saveResource(String path, IResource resource,
                                   io.nop.api.core.util.progress.IStepProgressListener listener,
                                   Map<String, Object> options) {
            throw new UnsupportedOperationException("tenant override store is read-only");
        }
    }

    static final class ResourceTenantPathHelper {
        static String toCanonicalPath(String tenantId, String tenantPrefixedPath) {
            String prefix = "/_tenant/" + tenantId;
            if (tenantPrefixedPath != null && tenantPrefixedPath.startsWith(prefix))
                return tenantPrefixedPath.substring(prefix.length());
            return null;
        }
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();

        // 锚点(1)：租户资源启用（活读：VFS 分路 + 本测试 modelType 的 cache 创建）
        tenantSwitch = CFG_TENANT_RESOURCE_ENABLED;
        tenantSwitchBaseline = tenantSwitch.get();
        AppConfig.getConfigProvider().updateConfigValue(tenantSwitch, true);

        // 锚点(4)：租户覆盖资源供给（租户 B 覆盖 = 不同源不同树）
        tenantOverrideStore = new TenantOverrideStore(TenantBindingTestUnit.TENANT_B);
        tenantOverrideStore.put(PATH, TenantBindingTestUnit.TENANT_B_SOURCE);
        oldProvider = ResourceTenantManager.instance().getTenantResourceProvider();
        ResourceTenantManager.instance().setTenantResourceProvider(new ITenantResourceProvider() {
            @Override
            public Set<String> getUsedTenantIds() {
                return Collections.singleton(TenantBindingTestUnit.TENANT_B);
            }

            @Override
            public IResourceStore getTenantResourceStore(String tenantId) {
                return TenantBindingTestUnit.TENANT_B.equals(tenantId) ? tenantOverrideStore : null;
            }

            @Override
            public void clearForTenant(String tenantId) {
                // 测试供给无状态可清
            }
        });

        // 锚点(5)：专用测试 modelType（fileType=itxpl；cache 在租户启用后首次创建 = per-tenant）
        ComponentModelConfig config = new ComponentModelConfig();
        config.setModelType("itest-xpl");
        config.loader("itxpl", new ComponentModelConfig.LoaderConfig("itxpl", null, null,
                Collections.emptyMap(), unsafeCast(new XplModelLoader(XLangOutputMode.none))));
        modelConfigCancellable = ResourceComponentManager.instance().registerComponentModelConfig(config);

        // 基资源入 VFS 内存层（标准路径；租户 A/无租户视角 = 基树 = 清单指纹源）
        IResourceStore existing = VirtualFileSystem.instance().getInMemoryLayer();
        if (existing instanceof InMemoryResourceStore) {
            inMemoryLayer = (InMemoryResourceStore) existing;
        } else {
            inMemoryLayer = new InMemoryResourceStore();
            VirtualFileSystem.instance().updateInMemoryLayer(inMemoryLayer);
            createdInMemoryLayer = true;
        }
        inMemoryLayer.addResource(baseResource = new InMemoryTextResource(PATH, TenantBindingTestUnit.BASE_SOURCE));

        // 双清单注入：扫描清单 + 生成类清单（基树指纹 + 夹具类——租户差异化树结构性无生成类）
        ProductionBindingCorpus.StaticUnit unit = ProductionBindingCorpus.standalone(
                PATH, TenantBindingTestUnit.BASE_SOURCE, TenantBindingTestUnit.MODE);
        String baseFingerprint = ExecutableTreeFingerprints.fingerprint(
                ProductionBindingCorpus.compileCanonicalTree(unit));
        GeneratedClassManifest manifest = GeneratedClassManifest.of(new LinkedHashMap<>(
                Map.of(PATH, new GeneratedClassManifest.Entry(PATH, FIXTURE_FQN, baseFingerprint))));
        JavaEvalExecutionBackend.instance().clearUnavailable();
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(manifest);
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.singletonList(PATH));
    }

    @AfterAll
    public static void resetEnvironment() {
        AppConfig.getConfigProvider().updateConfigValue(tenantSwitch, tenantSwitchBaseline);
        ResourceTenantManager.instance().setTenantResourceProvider(oldProvider);
        if (modelConfigCancellable != null)
            modelConfigCancellable.cancel();
        ResourceComponentManager.instance().clearCache("itest-xpl");
        inMemoryLayer.removeResource(baseResource);
        if (createdInMemoryLayer)
            VirtualFileSystem.instance().updateInMemoryLayer(null);
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        JavaEvalExecutionBackend.instance().clearUnavailable();
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    @SuppressWarnings("unchecked")
    private static io.nop.core.resource.IResourceObjectLoader<Object> unsafeCast(XplModelLoader loader) {
        return (io.nop.core.resource.IResourceObjectLoader<Object>) (io.nop.core.resource.IResourceObjectLoader<?>) loader;
    }

    private ListAppender<ILoggingEvent> appender;

    private Logger observationLogger;

    @BeforeEach
    public void setUp() {
        // 测试方法间隔离：清模型缓存（含 per-tenant 子缓存）与稳态去重集——
        // 观测/绑定断言对"本次加载"敏感，缓存复用会吞掉绑定期事件
        ResourceComponentManager.instance().clearCache("itest-xpl");
        EvalBackendObservation.clearSteadyStateDedup();
        observationLogger = (Logger) LoggerFactory.getLogger(EvalBackendObservation.class);
        appender = new ListAppender<>();
        appender.start();
        observationLogger.addAppender(appender);
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    @AfterEach
    public void tearDown() {
        observationLogger.detachAppender(appender);
        appender.stop();
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    private static XplModel loadForTenant(String tenantId) {
        return ContextProvider.runWithTenant(tenantId,
                () -> (XplModel) ResourceComponentManager.instance().loadComponentModel(PATH));
    }

    private static IEvalScope scopeWithX() {
        return EvalExprProvider.newEvalScope(Map.of("x", 10));
    }

    // ---- 租户 A：基树 → 绑定命中生成类（经 RCM 模型加载 + 绑定 hook 全链） ----

    @Test
    public void testTenantABindsGeneratedClass() {
        XplModel modelA = loadForTenant(TenantBindingTestUnit.TENANT_A);
        IExecutableExpression exprA = modelA.getExpr();
        assertTrue(exprA instanceof EvalStaticBoundExecutable, "tenant A (base tree) must bind");
        Method artifact = (Method) ((EvalStaticBoundExecutable) exprA).getBinding().getBindingArtifact();
        assertEquals(FIXTURE_FQN, artifact.getDeclaringClass().getName());

        Object result = modelA.invoke(scopeWithX());
        assertEquals(TenantBindingTestUnit.BASE_RESULT, result);

        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.STATIC, decision.getKind());
        assertFalse(decision.isDegraded());
    }

    // ---- 租户 B：覆盖树 → 稳态降级（tenant-divergent-tree）+ 解释器执行租户语义 ----

    @Test
    public void testTenantBDegradesToInterpreterWithSteadyStateObservation() {
        double before = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_TENANT_DIVERGENT_TREE);

        XplModel modelA = loadForTenant(TenantBindingTestUnit.TENANT_A);
        XplModel modelB = loadForTenant(TenantBindingTestUnit.TENANT_B);

        // 锚点(5)：per-tenant cache——同路径两租户模型实例隔离
        assertNotSame(modelA, modelB);

        IExecutableExpression exprB = modelB.getExpr();
        assertTrue(exprB instanceof EvalStaticDegradedExecutable,
                "tenant B (tenant-diverged tree) must degrade to interpreter");
        // 分级 reason 落在绑定期观测（counter/WARN，下方断言）；降级标记携带加载期裁定语义
        assertTrue(((EvalStaticDegradedExecutable) exprB).getReason().contains("load-time"));

        // 结果语义 = 租户 B 覆盖源码（31 ≠ 21 基树/生成类结果）——真实租户差异化语义
        Object resultB = modelB.invoke(scopeWithX());
        assertEquals(TenantBindingTestUnit.TENANT_B_RESULT, resultB);

        // 观测：稳态 reason 计数 +1、WARN 恰一次（once-per-path 去重）
        assertEquals(1.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_TENANT_DIVERGENT_TREE) - before, 1e-9);
        assertEquals(1, appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage()
                        .contains("reason=" + EvalBackendObservation.REASON_TENANT_DIVERGENT_TREE))
                .count(), "steady-state WARN deduped to once per path");

        // 租户 B 执行裁决：INTERPRETER + artifact = 原树（解释器兜底）
        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
        assertTrue(decision.isDegraded());
        assertSame(((EvalStaticDegradedExecutable) exprB).getSourceTree(), decision.getArtifact());
    }

    // ---- 互不串用：租户 B 加载后租户 A 仍绑定命中（缓存各自复用） ----

    @Test
    public void testNoCrossTenantLeak() {
        XplModel first = loadForTenant(TenantBindingTestUnit.TENANT_A);
        loadForTenant(TenantBindingTestUnit.TENANT_B);
        XplModel again = loadForTenant(TenantBindingTestUnit.TENANT_A);
        assertSame(first, again, "tenant A cache entry reused (no tenant-B pollution)");
        assertEquals(TenantBindingTestUnit.BASE_RESULT, again.invoke(scopeWithX()));
        assertTrue(again.getExpr() instanceof EvalStaticBoundExecutable);
    }
}
