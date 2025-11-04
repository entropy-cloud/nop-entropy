/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.model._gen._TaskInputModel;
import io.nop.xlang.xdsl.action.IActionInputModel;

public class TaskInputModel extends _TaskInputModel implements IActionInputModel {

    public TaskInputModel() {

    }

    public void normalize() {

    }

    public IEvalAction getValueExpr() {
        IEvalAction source = getSource();
        if (source == null) {
            source = getValue();
        }
        return source;
    }
}
