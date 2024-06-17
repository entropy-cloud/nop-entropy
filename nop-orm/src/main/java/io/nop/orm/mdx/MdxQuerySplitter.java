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
import io.nop.commons.collections.MutableIntArray;
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

    private MdxQueryBean mainQuery;

    private Map<String, String> sourceNames = new HashMap<>();

    public Map<String, String> getSourceNames() {
        return sourceNames;
    }

    public List<MdxQueryBean> split(QueryBean query, IEntityModel entityModel) {
        if (query.getSourceName() == null)
            query.setSourceName(entityModel.getName());

        List<MdxQueryBean> ret = new ArrayList<>();
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
                MdxQueryBean subQuery = getSubQuery(source);
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
                MdxQueryBean subQuery = getSubQuery(entry.getKey(), entry.getValue(), entityModel);
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

    private MdxQueryBean getMainQuery(QueryBean query, IEntityModel entityModel) {
        MdxQueryBean bean = new MdxQueryBean();
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

        MutableIntArray dimFieldIndexes = new MutableIntArray();
        if (!isEmpty(dimFields)) {
            // 将dimFields加入到mainFields中
            mainFields = addDimFields(mainFields, dimFields, dimFieldIndexes);
        }

        bean.setDimFields(dimFields);
        bean.setDimFieldIndexes(dimFieldIndexes);
        bean.setFields(mainFields);
        return bean;
    }

    /**
     * 将dimFields加入到fields列表张
     */
    private List<QueryFieldBean> addDimFields(List<QueryFieldBean> fields, List<String> dimFields,
                                              MutableIntArray dimFieldIndexes) {
        List<QueryFieldBean> ret = new ArrayList<>(fields.size() + dimFields.size());
        ret.addAll(fields);
        for (String dimField : dimFields) {
            int index = findField(fields, dimField);
            if (index < 0) {
                QueryFieldBean field = new QueryFieldBean();
                field.setName(dimField);
                field.setInternal(true);
                dimFieldIndexes.add(ret.size());
                ret.add(field);
            } else {
                dimFieldIndexes.add(index);
            }
        }
        return ret;
    }

    private int findField(List<QueryFieldBean> fields, String name) {
        for (int i = 0, n = fields.size(); i < n; i++) {
            QueryFieldBean field = fields.get(i);
            if (field.getName().equals(name))
                return i;
        }
        return -1;
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

    private MdxQueryBean getSubQuery(QuerySourceBean source) {
        List<QueryFieldBean> fields = subFieldsMap.remove(source.getAlias());
        if (fields != null) {
            MdxQueryBean query = new MdxQueryBean();
            query.setFilter(source.getFilter());
            query.setDimFields(source.getDimFields());
            fields = fields.stream().map(QueryFieldBean::cloneExceptOwner).collect(Collectors.toList());
            List<String> dimFields = source.getDimFields();
            if (isEmpty(dimFields)) {
                dimFields = getNonAggFields(fields);
            }
            MutableIntArray dimFieldIndexes = new MutableIntArray();
            query.setFields(addDimFields(fields, dimFields, dimFieldIndexes));
            query.setDimFieldIndexes(dimFieldIndexes);
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

    private MdxQueryBean getSubQuery(String alias, List<QueryFieldBean> fields, IEntityModel entityModel) {
        IEntityRelationModel rel = entityModel.getRelation(alias, false);
        MdxQueryBean query = new MdxQueryBean();
        query.setSourceName(rel.getRefEntityName());

        sourceNames.put(alias, query.getSourceName());

        List<String> dimFields = new ArrayList<>();

        // 先增加关联列
        for (IEntityJoinConditionModel join : rel.getJoin()) {
            if (join.getLeftProp() != null) {
                int idx = mainQuery.getDimFields().indexOf(join.getLeftProp());
                // 如果字段不在关联字段范围之内，则认为这个字段会被汇总掉
                if (idx < 0)
                    continue;
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

        // dimFields = reorder(dimFields, dimIdx);
        query.setDimFields(dimFields);

        if (!dimFieldsMatch(mainQuery, query))
            throw new NopException(ERR_ORM_QUERY_DIM_FIELDS_MISMATCH).param(ARG_SOURCE_NAME, mainQuery.getSourceName())
                    .param(ARG_SUB_SOURCE_NAME, query.getSourceName());

        List<QueryFieldBean> subFields = new ArrayList<>();
        for (QueryFieldBean field : fields) {
            subFields.add(field.cloneExceptOwner());
        }

        MutableIntArray dimIdx = new MutableIntArray();
        for (String dimField : dimFields) {
            int index = findField(fields, dimField);
            if (index < 0) {
                QueryFieldBean field = QueryFieldBean.forField(dimField);
                field.setInternal(true);
                dimIdx.add(subFields.size());
                subFields.add(field);
            } else {
                dimIdx.add(index);
            }
        }

        query.setFields(subFields);
        query.setDimFieldIndexes(dimIdx);

        for (String field : getNonAggFields(query.getFields())) {
            query.addGroupField(field);
        }

        return query;
    }
}