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
        assertFalse("cache must not contain the project before first access",
                ProjectFileHelper.cachedProjectKeys().contains(project));

        // 首次访问触发插入点登记（computeIfAbsent 新建条目 + tryRegister 子 Disposable）
        ProjectFileHelper.getCachedNopVfsPaths(project);
        assertTrue("cache must contain the project after first access",
                ProjectFileHelper.cachedProjectKeys().contains(project));

        // 项目级服务 dispose（项目关闭路径的 Disposable 树触发）→ 移除动作执行
        NopProjectService service = project.getService(NopProjectService.class);
        Disposer.dispose(service);

        assertFalse("cache entry must be removed after project dispose (C1-004 pairing)",
                ProjectFileHelper.cachedProjectKeys().contains(project));
    }

    /** CL-204 三态：READY 幂等（get() 重复调用不重入 init）；FAILED fail-fast 契约见下注 */
    @Test
    public void testGetIdempotentAfterReady() {
        Project project = getProject();
        NopProjectService first = NopProjectService.get();
        NopProjectService second = NopProjectService.get();
        assertTrue("get() must be idempotent after READY (three-state truncation)", first == second);

        // FAILED 态契约：任一步骤失败 → initState=FAILED，此后 get() fail-fast 抛
        // IllegalStateException 且 cause 携带初始化失败原因（不再静默返回半初始化服务）。
        // 注入形态依赖 Gradle harness 的失败 fixture（令 acquire 抛错的项目级配置），Maven 侧
        // 等价覆盖 = TestSharedCoreInitializerRegistrar 的失败替身两态（红⑤）+ registrar
        // destroyIfOrphaned 孤儿清理断言。
    }

    /**
     * W-05 接线冒烟：init→acquire 与 dispose→release 两路径经真实 NopProjectService 连通
     * （get() 成功即 acquire 链连通、dispose 不抛即 release 链经 cleanup 连通）。
     *
     * <p><b>验证盲区声明（closure audit M-2）</b>：CL-204 FAILED 态与「两项目交叉开/关全局设施
     * 存活」在本 harness 无可执行断言（需失败注入 fixture 与双项目 fixture）——该两面的
     * 计数语义由 Maven 侧 TestSharedCoreInitializerRegistrar 计数替身承载（红⑤），状态机与
     * 接线的组合行为属 Gradle 验证盲区（通道③ 如实登记，禁伪绿）。</p>
     */
    @Test
    public void testRegistrarWiringThroughProjectInitAndDispose() {
        Project project = getProject();

        // init→acquire 链：get() 成功即经 registrar 持有全局设施
        NopProjectService service = NopProjectService.get();

        // dispose→release 链：经 cleanup 登记的凭据释放不抛（计数-1）
        Disposer.dispose(service);
    }
}
