/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.orm.model._gen._OrmToManyReferenceModel;

public class OrmToManyReferenceModel extends _OrmToManyReferenceModel {
    private String collectionName;

    public OrmToManyReferenceModel() {

    }

    @Override
    public OrmDataTypeKind getKind() {
        return OrmDataTypeKind.TO_MANY_RELATION;
    }

    @Override
    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

}
