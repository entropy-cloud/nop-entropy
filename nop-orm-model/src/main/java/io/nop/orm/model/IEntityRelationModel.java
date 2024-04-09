/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.api.core.beans.query.OrderFieldBean;

import java.util.List;

public interface IEntityRelationModel extends IEntityPropModel {

    String getRefPropName();

    IEntityModel getOwnerEntityModel();

    IEntityModel getRefEntityModel();

    /**
     * 当ownerId只对应单个字段时返回非空值
     *
     * @return
     */
    IEntityJoinConditionModel getSingleColumnJoin();

    default IColumnModel getSingleJoinColumn() {
        int propId = getColumnPropId();
        return propId <= 0 ? null : getOwnerEntityModel().getColumnByPropId(propId, false);
    }

    /**
     * refEntity上的关联数据列
     */
    int[] getRefPropIds();

    List<OrderFieldBean> getSort();

    String getRefEntityName();

    String getCollectionName();

    String getPersistDriver();

    String getKeyProp();

    boolean isOneToOne();

    boolean isMandatory();

    boolean isReverseDepends();

    Integer getMaxBatchLoadSize();

    /**
     * join条件已经按照关联实体的主键字段的顺序排好序。
     */
    List<? extends IEntityJoinConditionModel> getJoin();

    boolean isCascadeDelete();

    boolean isAutoCascadeDelete();

    /**
     * 每次执行都可能返回不同的结果，不能缓存到实体内部
     */
    boolean isDynamicRelation();

    boolean isUseGlobalCache();

    /**
     * 使用计算属性或者alias属性进行join
     */
    default boolean isDynamicJoin() {
        for (IEntityJoinConditionModel on : getJoin()) {
            if (on.getLeftPropModel() != null && !on.getLeftPropModel().isColumnModel())
                return true;
            if (on.getRightPropModel() != null && !on.getRightPropModel().isColumnModel())
                return true;
        }
        return false;
    }

    /**
     * 是否关联到非主键字段
     */
    boolean isJoinOnNonPkColumn();
}