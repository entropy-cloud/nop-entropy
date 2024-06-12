/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
        // 规范化属性名为属性模型上的名称。例如id可能定义的属性名是sid
        if (leftPropModel != null) {
            this.setLeftProp(leftPropModel.getName());
        }
    }

    @Override
    public IEntityPropModel getRightPropModel() {
        return rightPropModel;
    }

    public void setRightPropModel(IEntityPropModel rightPropModel) {
        this.rightPropModel = rightPropModel;
        if (rightPropModel != null) {
            this.setRightProp(rightPropModel.getName());
        }
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
