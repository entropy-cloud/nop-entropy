/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 辅助处理ManyToMany中间表的增删改查
 *
 * @param <R> 中间表实体类型
 */
public class ManyToManyTool<R extends IOrmEntity> {
    private final IEntityDao<R> dao;
    private final String leftProp;
    private final String rightProp;

    public ManyToManyTool(IDaoProvider daoProvider, String relationEntityName, String leftProp, String rightProp) {
        this.leftProp = leftProp;
        this.rightProp = rightProp;
        this.dao = daoProvider.dao(relationEntityName);
    }

    public void removeRelation(
            Object leftValue, Object rightValue) {
        R example = dao.newEntity();
        example.orm_propValueByName(leftProp, leftValue);
        example.orm_propValueByName(rightProp, rightValue);
        R rel = dao.findFirstByExample(example);
        if (rel != null) {
            dao.deleteEntity(rel);
        }
    }

    public void removeRelations(
            Object leftValue, Collection<?> rightValues, TreeBean filter) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(leftProp, leftValue));
        query.addFilter(FilterBeans.in(rightProp, rightValues));
        if (filter != null)
            query.addFilter(filter);

        List<R> relations = dao.findAllByQuery(query);
        dao.batchDeleteEntities(relations);
    }

    public void addRelations(Object leftValue, Collection<?> rightValues, TreeBean filter) {
        Map<String, Object> fixedProps = new HashMap<>();
        fixedProps.put(leftProp, leftValue);
        updateRelations(fixedProps, filter, false, rightProp, rightValues);
    }

    public void updateRelations(
            Object leftValue, Collection<?> rightValues, TreeBean filter) {
        Map<String, Object> fixedProps = new HashMap<>();
        fixedProps.put(leftProp, leftValue);
        updateRelations(fixedProps, filter, true, rightProp, rightValues);
    }

    public List<R> getRelations(String leftProp, Object leftValue) {
        R example = dao.newEntity();
        example.orm_propValueByName(leftProp, leftValue);
        return dao.findAllByExample(example);
    }

    public void updateRelations(
            Map<String, Object> fixedProps,
            TreeBean filter,
            boolean deleteUnknown,
            String relProp, Collection<?> relValues) {
        if (relValues == null) {
            relValues = Collections.emptyList();
        }

        QueryBean query = new QueryBean();
        for (Map.Entry<String, Object> entry : fixedProps.entrySet()) {
            query.addFilter(FilterBeans.eq(entry.getKey(), entry.getValue()));
        }
        if (filter != null) {
            query.addFilter(filter);
        }

        List<R> relations = dao.findAllByQuery(query);
        relValues = CollectionHelper.toStringList(relValues);

        for (R relation : relations) {
            String relValue = ConvertHelper.toString(relation.orm_propValueByName(relProp));
            if (!relValues.remove(relValue)) {
                if (deleteUnknown)
                    dao.deleteEntity(relation);
            }
        }

        for (Object relValue : relValues) {
            R relation = dao.newEntity();
            for (Map.Entry<String, Object> entry : fixedProps.entrySet()) {
                relation.orm_propValueByName(entry.getKey(), entry.getValue());
            }
            relation.orm_propValueByName(relProp, relValue);
            dao.saveEntity(relation);
        }
    }
}
