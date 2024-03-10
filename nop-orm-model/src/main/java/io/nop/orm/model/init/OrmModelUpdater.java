/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.init;

import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmModel;

import java.util.Set;

public class OrmModelUpdater {
    private final IOrmModel ormModel;

    public OrmModelUpdater(IOrmModel ormModel) {
        this.ormModel = ormModel;
    }

    public OrmModel updateDynamicModel(Set<String> moduleNames, IOrmModel dynModel) {
        return null;
    }
}
