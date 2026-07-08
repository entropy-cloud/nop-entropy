/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

import java.util.Collections;
import java.util.List;

/**
 * 任务分配计划。空 plan 表示没有 fitting worker。
 */
@DataBean
public class AssignmentPlan {
    private final List<Assignment> assignments;

    public AssignmentPlan(@JsonProperty("assignments") List<Assignment> assignments) {
        this.assignments = assignments != null ? assignments : Collections.emptyList();
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public boolean isEmpty() {
        return assignments.isEmpty();
    }

    public static AssignmentPlan empty() {
        return new AssignmentPlan(Collections.emptyList());
    }
}
