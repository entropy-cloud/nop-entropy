/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.core.reflect.hook.IExtensibleObject;
import io.nop.core.type.IGenericType;

public interface ITaskInputModel extends IExtensibleObject {
    String getName();

    IGenericType getType();

    boolean isMandatory();
}