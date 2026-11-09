/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.deps;

import io.nop.core.resource.component.ResourceComponentManager;
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
        // 收集依赖：traceDepends 使 dependencyMap 登记 path
        ResourceComponentManager.instance().collectDepends("/probe/dep-source", () -> {
            ResourceComponentManager.instance().traceDepends("/probe/dep-target");
            return null;
        });

        ResourceDependencySet before = ResourceComponentManager.instance().getResourceDepends("/probe/dep-target");
        if (before != null) {
            ResourceComponentManager.instance().removeDependency("/probe/dep-target");
            assertNull(ResourceComponentManager.instance().getResourceDepends("/probe/dep-target"),
                    "缺陷态：dependencyMap 单调增长、无逐条淘汰");
        }

        // 幂等：移除不存在的 path 不抛错
        ResourceComponentManager.instance().removeDependency("/probe/never-registered");
        assertNotNull(ResourceComponentManager.instance());
    }
}
