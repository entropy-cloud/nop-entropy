/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model;

import io.nop.api.core.util.INeedInit;
import io.nop.wf.core.model._gen._WfModel;
import io.nop.wf.core.model.analyze.WfModelAnalyzer;

public class WfModel extends _WfModel implements IWorkflowModel, INeedInit {
    public WfModel() {

    }

    public WfStepModel getStartStep() {
        return getStep(getStart().getStartStepName());
    }

    public void init() {
        new WfModelAnalyzer().analyze(this);
    }
}
