/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.orm.model._gen._OrmToOneReferenceModel;

public class OrmToOneReferenceModel extends _OrmToOneReferenceModel {

    public OrmToOneReferenceModel() {

    }

    @Override
    public boolean isUseGlobalCache() {
        return false;
    }

    @Override
    public OrmDataTypeKind getKind() {
        return OrmDataTypeKind.TO_ONE_RELATION;
    }
}
