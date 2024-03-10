/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.commons.type.StdDataType;

public interface IEntityJoinConditionModel {
    IEntityPropModel getLeftPropModel();

    IEntityPropModel getRightPropModel();

    default IEntityPropModel getRightDisplayPropModel() {
        return getRightPropModel().getOwnerEntityModel().getDisplayPropModel();
    }

    String getLeftProp();

    String getRightProp();

    StdDataType getLeftType();

    StdDataType getRightType();

    Object getLeftValue();

    Object getRightValue();

//    Object getLeftValue(IOrmEntity entity);
//
//    Object getRightValue(IOrmEntity refEntity);
}
