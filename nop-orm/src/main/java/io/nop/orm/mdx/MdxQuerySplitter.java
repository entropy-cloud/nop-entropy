/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.mdx;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.GroupFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.api.core.beans.query.QuerySourceBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.orm.dao.DaoQueryHelper;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.commons.util.CollectionHelper.isEmpty;
import static io.nop.commons.util.CollectionHelper.safeGetSize;
import static io.nop.orm.OrmErrors.ARG_COLLECTION_NAME;
import static io.nop.orm.OrmErrors.ARG_SOURCE_NAME;
import static io.nop.orm.OrmErrors.ARG_SUB_SOURCE_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_DIM_FIELDS_MISMATCH;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_INVALID_JOIN;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_NO_DIM_FIELDS;

/**
 * 将QueryBean按照主子表拆开
 */
public class MdxQuerySplitter {
    static final Logger LOG = LoggerFactory.getLogger(MdxQuerySplitter.class);

    private List<QueryFieldBean> mainFields = new ArrayList<>();
    private Map<String, List<QueryFieldBean>> subFieldsMap = new HashMap<>();

    private QueryBean mainQuery;

    private Map<String, String> sourceNames = new HashMap<>();

    public Map<String, String> getSourceNames() {
        return sourceNames;
    }

    public List<QueryBean> split(QueryBean query, IEntityModel entityModel) {
        if (query.getSourceName() == null)
            query.setSourceName(entityModel.getName());

        List<QueryBean> ret = new ArrayList<>();
        splitFields(query);
        mainQuery = getMainQuery(query, entityModel);
        ret.add(mainQuery);

        if (!subFieldsMap.isEmpty()) {
            if (isEmpty(mainQuery.getDimFields()))
                throw new NopException(ERR_ORM_QUERY_NO_DIM_FIELDS).param(ARG_SOURCE_NAME, mainQuery.getSourceName());
        }

        if (query.getJoins() != null) {
            // 自定义关联子表，通过dimFields对齐来实现join
            for (QuerySourceBean source : query.getJoins()) {
                QueryBean subQuery = getSubQuery(source);
                if (subQuery != null) {
                    ret.add(subQuery);

                    if (!dimFieldsMatch(mainQuery, subQuery))
                        throw new NopException(ERR_ORM_QUERY_DIM_FIELDS_MISMATCH)
                                .param(ARG_SOURCE_NAME, mainQuery.getSourceName())
                                .param(ARG_SUB_SOURCE_NAME, subQuery.getSourceName());
                }
            }
        }

        if (!subFieldsMap.isEmpty()) {
            // 具有owner的字段，但是没有对应的source配置，则查找一对多属性，尝试从关联关系配置中查找到关联对象
            for (Map.Entry<String, List<QueryFieldBean>> entry : subFieldsMap.entrySet()) {
                QueryBean subQuery = getSubQuery(entry.getKey(), entry.getValue(), entityModel);
                ret.add(subQuery);
            }
        }

        return ret;
    }

    private boolean dimFieldsMatch(QueryBean query, QueryBean subQuery) {
        List<String> dimFields = query.getDimFields();
        List<String> subDimFields = subQuery.getDimFields();

        return safeGetSize(dimFields) == safeGetSize(subDimFields);
    }

    private QueryBean getMainQuery(QueryBean query, IEntityModel entityModel) {
        QueryBean bean = new QueryBean();
        bean.setSourceName(query.getSourceName());
        bean.setFilter(query.getFilter());
        bean.setCursor(query.getCursor());
        bean.setGroupBy(query.getGroupBy());
        bean.setOrderBy(query.getOrderBy());
        bean.setLimit(query.getLimit());
        bean.setOffset(query.getOffset());

        List<String> dimFields = query.getDimFields();

        // 如果具有聚合计算字段，则所有非聚合字段必须在groupBy中
        boolean agg = containsAggFunc(mainFields);
        if (agg) {
            List<String> nonAggFields = getNonAggFields(mainFields);
            for (String field : nonAggFields) {
                bean.addGroupField(field);
            }
        }

        if (isEmpty(dimFields)) {
            if (!bean.hasGroupBy()) {
                // 如果没有聚合计算，则以主键为dimFields
                dimFields = entityModel.getPkColumnNames();
            } else {
                // 具有group by，但是没有dimFields，则以group by的字段为dimFields
                dimFields = bean.getGroupBy().stream().map(GroupFieldBean::getName).collect(Collectors.toList());
            }
        }

        if (!isEmpty(dimFields)) {
            // 确保dimFields排在字段列表的最前面
            mainFields = addDimFields(mainFields, dimFields);
        }

        bean.setDimFields(dimFields);
        bean.setFields(mainFields);
        return bean;
    }

    /**
     * 重排fields, 确保dimFields排在最前面
     */
    private List<QueryFieldBean> addDimFields(List<QueryFieldBean> fields, List<String> dimFields) {
        List<QueryFieldBean> ret = new ArrayList<>(fields.size() + dimFields.size());
        for (String dimField : dimFields) {
            QueryFieldBean field = new QueryFieldBean();
            field.setName(dimField);
            field.setInternal(true);
            ret.add(field);
        }
        for (QueryFieldBean field : fields) {
            if (field.getAggFunc() == null) {
                int index = dimFields.indexOf(field.getName());
                if (index >= 0) {
                    ret.get(index).setInternal(field.isInternal());
                } else {
                    ret.add(field);
                }
            }
        }
        return ret;
    }

    private void splitFields(QueryBean query) {
        Map<String, QueryFieldBean> map = new HashMap<>();

        List<QueryFieldBean> fields = query.getFields();
        for (QueryFieldBean field : fields) {
            if (!StringHelper.isEmpty(field.getOwner())) {
                DaoQueryHelper.checkOwnerName(field.getOwner());
                // 具有owner的字段都认为是子表的字段，需要单独去获取
                subFieldsMap.computeIfAbsent(field.getOwner(), k -> new ArrayList<>()).add(field);
            } else {
                mainFields.add(field);
                map.put(field.getLabel(), field);
            }
        }
    }

    private boolean containsAggFunc(List<QueryFieldBean> fields) {
        return fields.stream().anyMatch(fld -> fld.getAggFunc() != null);
    }

    private List<String> getNonAggFields(List<QueryFieldBean> fields) {
        List<String> ret = new ArrayList<>();
        for (QueryFieldBean field : fields) {
            if (field.getAggFunc() == null) {
                ret.add(field.getName());
            }
        }
        return ret;
    }

    private QueryBean getSubQuery(QuerySourceBean source) {
        List<QueryFieldBean> fields = subFieldsMap.remove(source.getAlias());
        if (fields != null) {
            QueryBean query = new QueryBean();
            query.setFilter(source.getFilter());
            query.setDimFields(source.getDimFields());
            fields = fields.stream().map(QueryFieldBean::cloneExceptOwner).collect(Collectors.toList());
            List<String> dimFields = source.getDimFields();
            if (isEmpty(dimFields)) {
                dimFields = getNonAggFields(fields);
            }
            query.setFields(addDimFields(fields, dimFields));
            query.setSourceName(source.getSourceName());

            sourceNames.put(source.getAlias(), source.getSourceName());

            for (String field : getNonAggFields(query.getFields())) {
                query.addGroupField(field);
            }
            return query;
        } else {
            LOG.error("nop.sub-query-alias-is-not-used:alias={},sourceName={}", source.getAlias(),
                    source.getSourceName());
        }

        return null;
    }

    private QueryBean getSubQuery(String alias, List<QueryFieldBean> fields, IEntityModel entityModel) {
        IEntityRelationModel rel = entityModel.getRelation(alias, false);
        QueryBean query = new QueryBean();
        query.setSourceName(rel.getRefEntityName());

        sourceNames.put(alias, query.getSourceName());

        List<String> dimFields = new ArrayList<>();
        List<Integer> dimIdx = new ArrayList<>();

        // 先增加关联列
        for (IEntityJoinConditionModel join : rel.getJoin()) {
            if (join.getLeftProp() != null) {
                int idx = mainQuery.getDimFields().indexOf(join.getLeftProp());
                if (idx < 0)
                    throw new NopException(ERR_ORM_QUERY_DIM_FIELDS_MISMATCH)
                            .param(ARG_SOURCE_NAME, mainQuery.getSourceName())
                            .param(ARG_SUB_SOURCE_NAME, query.getSourceName());
                dimIdx.add(idx);
                if (join.getRightProp() != null) {
                    dimFields.add(join.getRightProp());
                } else {
                    throw new NopException(ERR_ORM_QUERY_INVALID_JOIN).param(ARG_COLLECTION_NAME,
                            rel.getCollectionName());
                }
            } else {
                query.addFilter(FilterBeans.eq(join.getRightProp(), join.getLeftValue()));
            }
        }

        dimFields = reorder(dimFields, dimIdx);
        query.setDimFields(dimFields);

        if (!dimFieldsMatch(mainQuery, query))
            throw new NopException(ERR_ORM_QUERY_DIM_FIELDS_MISMATCH).param(ARG_SOURCE_NAME, mainQuery.getSourceName())
                    .param(ARG_SUB_SOURCE_NAME, query.getSourceName());

        List<QueryFieldBean> subFields = new ArrayList<>();
        for (String dimField : dimFields) {
            QueryFieldBean field = QueryFieldBean.forField(dimField);
            field.setInternal(true);
            subFields.add(field);
        }

        for (QueryFieldBean field : fields) {
            subFields.add(field.cloneExceptOwner());
        }
        query.setFields(subFields);

        // 一对多关联按照主表字段进行汇总
        if (rel.isToManyRelation()) {
            for (String dimField : dimFields) {
                query.addGroupField(dimField);
            }
        }

        return query;
    }

    List<String> reorder(List<String> fields, List<Integer> idxList) {
        if (idxList.size() <= 1)
            return fields;

        List<String> ret = new ArrayList<>(fields.size());
        for (int idx : idxList) {
            ret.add(fields.get(idx));
        }
        return ret;
    }
}