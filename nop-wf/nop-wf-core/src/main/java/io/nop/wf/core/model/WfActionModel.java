/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model;

import io.nop.wf.core.model._gen._WfActionModel;

public class WfActionModel extends _WfActionModel implements IWorkflowActionModel, Comparable<WfActionModel> {
    public WfActionModel() {

    }

    @Override
    public int compareTo(WfActionModel o) {
        return Integer.compare(getSortOrder(), o.getSortOrder());
    }
}