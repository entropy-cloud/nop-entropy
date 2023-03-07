/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model;

import io.nop.wf.core.model._gen._WfTransitionToEmptyModel;

public class WfTransitionToEmptyModel extends _WfTransitionToEmptyModel {
    public WfTransitionToEmptyModel() {

    }

    @Override
    public WfTransitionToType getType() {
        return WfTransitionToType.TO_EMPTY;
    }
}
