/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.type.IGenericType;

import java.util.List;
import java.util.Map;

public interface IComputePropModel extends IEntityPropModel {
    List<? extends IComputedArgModel> getArgs();

    IEvalAction getGetter();

    IEvalAction getSetter();

    IGenericType getType();

    Object getValue(Object entity);

    void setValue(Object entity, Object value);

    Object computeValue(Object entity, Map<String, Object> args);
}