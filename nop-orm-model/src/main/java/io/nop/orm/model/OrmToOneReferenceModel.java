/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.orm.model._gen._OrmToOneReferenceModel;

public class OrmToOneReferenceModel extends _OrmToOneReferenceModel {
    private boolean oneToOne;

    public OrmToOneReferenceModel() {

    }

    @Override
    public boolean isUseGlobalCache() {
        return false;
    }

    @Override
    public boolean isOneToOne() {
        return oneToOne;
    }

    public void setOneToOne(boolean oneToOne) {
        checkAllowChange();
        this.oneToOne = oneToOne;
    }

    @Override
    public OrmDataTypeKind getKind() {
        return OrmDataTypeKind.TO_ONE_RELATION;
    }
}
