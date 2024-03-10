/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.model;


import io.nop.core.lang.eval.IEvalAction;

import java.io.Serializable;
import java.util.Map;

/**
 * @author canonical_entropy@163.com
 */
public interface IWorkflowAssignmentItemModel extends Serializable {
    String getActorType();

    String getActorId();

    String getDeptId();

    boolean isDynamic();

    boolean isSelectUser();

    Map<String, Object> getParams();

    IEvalAction getDynamicXpl();

    //List<IWfActor> buildActors(IWorkflow wfRt);
}