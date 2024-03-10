/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.core.lang.eval.IEvalAction;

public interface ITaskOutputModel extends ISourceLocationGetter {
    String getName();

    IEvalAction getSource();

    boolean isForAttr();
}
