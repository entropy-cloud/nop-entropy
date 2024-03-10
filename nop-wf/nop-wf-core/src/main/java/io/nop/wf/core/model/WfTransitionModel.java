/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.model;

import io.nop.api.core.util.INeedInit;
import io.nop.wf.core.model._gen._WfTransitionModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WfTransitionModel extends _WfTransitionModel implements INeedInit {
    private List<WfTransitionToModel> tos = null;

    public WfTransitionModel() {

    }

    public void init() {
        List<WfTransitionToModel> tos = new ArrayList<>();
        if (this.getToAssigned() != null)
            tos.add(this.getToAssigned());
        tos.addAll(this.getToSteps());
        if (this.getToEmpty() != null)
            tos.add(this.getToEmpty());
        if (this.getToEnd() != null) {
            tos.add(this.getToEnd());
        }
        tos.sort(Comparator.comparingInt(WfTransitionToModel::getOrder));
        this.tos = tos;
    }

    public List<WfTransitionToModel> getTransitionTos() {
        if (tos == null)
            init();
        return this.tos;
    }
}
