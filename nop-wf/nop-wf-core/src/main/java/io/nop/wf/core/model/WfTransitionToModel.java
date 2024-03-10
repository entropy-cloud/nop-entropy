/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.model;

import io.nop.wf.core.model._gen._WfTransitionToModel;

public class WfTransitionToModel extends _WfTransitionToModel implements IWorkflowConditionalModel {
    public WfTransitionToModel() {

    }

    /**
     * 是否回退链接，在计算流程图的生成树的时候会忽略backLink的连接
     */
    public boolean isBackLink(){
        return false;
    }

    public void setBackLink(boolean backLink){
    }

    public String getStepName(){
        return null;
    }

    public WfTransitionToType getType() {
        return WfTransitionToType.TO_STEP;
    }
}
