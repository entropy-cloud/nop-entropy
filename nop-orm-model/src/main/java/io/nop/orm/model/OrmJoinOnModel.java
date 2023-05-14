/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.commons.type.StdDataType;
import io.nop.orm.model._gen._OrmJoinOnModel;

public class OrmJoinOnModel extends _OrmJoinOnModel implements IEntityJoinConditionModel {
    private IEntityPropModel leftPropModel;
    private IEntityPropModel rightPropModel;

    public OrmJoinOnModel() {

    }

    @Override
    public IEntityPropModel getLeftPropModel() {
        return leftPropModel;
    }

    public void setLeftPropModel(IEntityPropModel leftPropModel) {
        this.leftPropModel = leftPropModel;
    }

    @Override
    public IEntityPropModel getRightPropModel() {
        return rightPropModel;
    }

    public void setRightPropModel(IEntityPropModel rightPropModel) {
        this.rightPropModel = rightPropModel;
    }

    @Override
    public StdDataType getLeftType() {
        if (leftPropModel == null)
            return rightPropModel.getStdDataType();
        return leftPropModel.getStdDataType();
    }

    @Override
    public StdDataType getRightType() {
        if (rightPropModel == null)
            return leftPropModel.getStdDataType();
        return rightPropModel.getStdDataType();
    }
//
//    @Override
//    public Object getLeftValue(IOrmEntity entity) {
//        if (leftPropModel != null)
//            return leftPropModel.getPropValue(entity);
//        return getLeftValue();
//    }
//
//    @Override
//    public Object getRightValue(IOrmEntity refEntity) {
//        if (rightPropModel != null)
//            return rightPropModel.getPropValue(refEntity);
//        return getRightValue();
//    }
}
