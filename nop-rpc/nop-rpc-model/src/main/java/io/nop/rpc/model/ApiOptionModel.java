/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

import io.nop.api.core.util.Guard;
import io.nop.rpc.model._gen._ApiOptionModel;

public class ApiOptionModel extends _ApiOptionModel {
    public ApiOptionModel() {

    }

    public static ApiOptionModel of(String name, Object value) {
        Guard.notEmpty(name, "name");

        ApiOptionModel option = new ApiOptionModel();
        option.setName(name);
        option.setValue(value);
        return option;
    }
}
