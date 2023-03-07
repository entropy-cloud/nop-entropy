/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.resource;

import com.intellij.openapi.project.Project;

import java.util.function.Supplier;

public class ProjectEnv {
    private static final ThreadLocal<Project> s_project = new ThreadLocal<>();

    public static <T> T withProject(Project project, Supplier<T> task) {
        Project proj = s_project.get();
        s_project.set(project);
        try {
            return task.get();
        } finally {
            s_project.set(proj);
        }
    }

    public static Project currentProject() {
        return s_project.get();
    }
}
