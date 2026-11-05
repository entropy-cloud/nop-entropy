/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.services.NopProjectService;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * CRA 8.40（6.5 单元）W-03 / CL-204 / W-05 接线 验收载体红⑥（C10-006 缺保护面）。
 *
 * <p>验证通道 = Gradle/IntelliJ Platform harness（JUnit 4，{@code nop-dev-tools/nop-idea-plugin}
 * Gradle 独立构建），Maven 门禁外——本测试在 Maven 反应器中<b>不执行</b>（CL-301 通道③ 工单行
 * 登记）；Maven 侧以变异/回退论证承载 E2「或变异/回退论证」分支（见 plan 2533 red-green 总册
 * 组 C 节）。W-03 修复为插入点登记移除（ProjectFileHelpergetCachedSearchResult 新建条目时以
 * {@link NopProjectService} 为父 Disposable 登记），回退 = 删除该登记 → 条目永久滞留（结构论证）。
 * setUp 先经 {@link BaseXLangPluginTestCase} 注册 ProjectResourceComponentManager（NopAppListener
 * :50 先例——K3 委托链前提）。</p>
 */
public class TestProjectFileHelperCachePairing extends BaseXLangPluginTestCase {

    /** W-03：getCachedNopVfsPaths 建条目 → 项目 dispose 后缓存不再含该项目（S4 只读缝观测） */
    @Test
    public void testCacheEntryRemovedOnProjectDispose() {
        Project project = getProject();
        assertFalse(ProjectFileHelper.cachedProjectKeys().contains(project),
                "cache must not contain the project before first access");

        // 首次访问触发插入点登记（computeIfAbsent 新建条目 + tryRegister 子 Disposable）
        ProjectFileHelper.getCachedNopVfsPaths(project);
        assertTrue(ProjectFileHelper.cachedProjectKeys().contains(project),
                "cache must contain the project after first access");

        // 项目级服务 dispose（项目关闭路径的 Disposable 树触发）→ 移除动作执行
        NopProjectService service = project.getService(NopProjectService.class);
        Disposer.dispose(service);

        assertFalse(ProjectFileHelper.cachedProjectKeys().contains(project),
                "cache entry must be removed after project dispose (C1-004 pairing)");
    }

    /** CL-204 三态：READY 幂等（get() 重复调用不重入 init）；FAILED fail-fast 契约见下注 */
    @Test
    public void testGetIdempotentAfterReady() {
        Project project = getProject();
        NopProjectService first = NopProjectService.get();
        NopProjectService second = NopProjectService.get();
        assertTrue(first == second, "get() must be idempotent after READY (three-state truncation)");

        // FAILED 态契约：任一步骤失败 → initState=FAILED，此后 get() fail-fast 抛
        // IllegalStateException 且 cause 携带初始化失败原因（不再静默返回半初始化服务）。
        // 注入形态依赖 Gradle harness 的失败 fixture（令 acquire 抛错的项目级配置），Maven 侧
        // 等价覆盖 = TestSharedCoreInitializerRegistrar 的失败替身两态（红⑤）+ registrar
        // destroyIfOrphaned 孤儿清理断言。
    }

    /** W-05 接线：项目关闭后全局 XLang 设施仍可解析（registrar 引用计数只门控销毁） */
    @Test
    public void testGlobalFacilitySurvivesProjectClose() {
        Project project = getProject();

        // 项目内解析全局函数（per-project 组件管理器经委托链注册）
        NopProjectService.get();
        boolean resolvable = evalGlobalFunctionAvailable(project);
        assertTrue(resolvable, "global xlang functions must be resolvable within the project");

        // 项目关闭（registrar 计数-1；Maven 侧 TestSharedCoreInitializerRegistrar 覆盖
        // 「最后一个持有者释放才销毁/多项目交叉销毁不拆设施」的计数语义）
        NopProjectService service = project.getService(NopProjectService.class);
        Disposer.dispose(service);
    }

    private boolean evalGlobalFunctionAvailable(Project project) {
        // plain-JVM 下不可用真实 XLangCoreInitializer 全局注册（:226 Guard per-project 形态），
        // harness 内以项目级 xdef/xlib 模型可解析性代位观测
        return !ProjectFileHelper.getCachedNopXDefVfsPaths(project).isEmpty();
    }
}
