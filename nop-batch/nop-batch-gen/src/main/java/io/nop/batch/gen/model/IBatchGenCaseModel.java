/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.gen.model;

import io.nop.api.core.beans.TreeBean;
import io.nop.core.type.IGenericType;

import java.util.List;
import java.util.Map;

public interface IBatchGenCaseModel {
    String getName();

    String getDescription();

    IGenericType getBeanType();

    Map<String, Object> getTemplate();

    TreeBean getWhen();

    boolean isSequential();

    List<BatchGenCaseModel> getSubCases();

    Map<String, Object> getOutputVars();

    default Map<String, Object> getMergedTemplate() {
        return getTemplate();
    }

    default Map<String, Object> getMergedOutputVars() {
        return getOutputVars();
    }
}
