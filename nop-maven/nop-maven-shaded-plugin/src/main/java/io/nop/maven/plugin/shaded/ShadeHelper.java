/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.maven.plugin.shaded;

import org.apache.maven.plugins.shade.relocation.Relocator;

import java.util.List;

public class ShadeHelper {
    public static String relocate(String name, List<Relocator> relocatorList) {
        for (Relocator relocator : relocatorList) {
            if (!relocator.canRelocateClass(name))
                continue;

            String relocated = relocator.relocateClass(name);
            if (relocated != null)
                return relocated;
        }
        return name;
    }
}
