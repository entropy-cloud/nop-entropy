/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.deps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * plan 2548 R4-14（C7-008）dependencyMap 逐条淘汰载体钉。
 */
public class TestResourceDependsManagerEvict {

    /** pin7008：removeDependency 后 dependencyMap 不再含该 path（缺陷态=只有 clearDependencies 全量清）。 */
    @Test
    public void pin7008_removeDependencyEvictsPerPath() {
        // checker 覆写 resolveResource：VFS 未初始化时默认实现返回 null → makeDepends 落空，
        // dep-target 的依赖集不会登记（载体必须显式提供可解析引用）
        ResourceDependsManager manager = new ResourceDependsManager(new DefaultResourceChangeChecker() {
            @Override
            public io.nop.api.core.resource.IResourceReference resolveResource(String resourcePath) {
                return new TestResourceDependsInvalidation.FlipReference(resourcePath, 1000L);
            }
        });
        // dependencyMap 只登记「collectDepends 栈顶资源」的依赖集（updateDepends 在 finally merge）
        TestResourceDependsInvalidation.FlipReference ref =
                new TestResourceDependsInvalidation.FlipReference("/probe/dep-target", 1000L);
        manager.collectDepends(ref, () -> "loaded");

        ResourceDependencySet before = manager.getResourceDepends("/probe/dep-target");
        assertNotNull(before, "依赖注册应成功（否则载体静默空转）");
        manager.removeDependency("/probe/dep-target");
        assertNull(manager.getResourceDepends("/probe/dep-target"),
                "缺陷态：dependencyMap 单调增长、无逐条淘汰");

        // 幂等：移除不存在的 path 不抛错
        manager.removeDependency("/probe/never-registered");
    }
}
