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
import io.nop.api.core.beans.query.QueryJoinConditionBean;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.commons.util.CollectionHelper.isEmpty;
import static io.nop.commons.util.CollectionHelper.safeGetSize;
import static io.nop.commons.util.CollectionHelper.toNotNull;
import static io.nop.orm.OrmErrors.ARG_ATTR;
import static io.nop.orm.OrmErrors.ARG_COLLECTION_NAME;
import static io.nop.orm.OrmErrors.ARG_LABEL;
import static io.nop.orm.OrmErrors.ARG_SOURCE_NAME;
import static io.nop.orm.OrmErrors.ARG_SUB_SOURCE_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_DIM_FIELDS_MISMATCH;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_DUPLICATE_FIELD_LABEL;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_INVALID_JOIN;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_NO_DIM_FIELDS;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_UNSUPPORTED_FIELD_ATTR;

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
        if (!isEmpty(query.getAggregates()))
            throw new NopException(ERR_ORM_QUERY_UNSUPPORTED_FIELD_ATTR).param(ARG_ATTR, "aggregates");

        String sourceName = query.getSourceName() == null ? entityModel.getName() : query.getSourceName();

        List<MdxQueryBean> ret = new ArrayList<>();
        splitFields(query);
        mainQuery = getMainQuery(query, entityModel, sourceName);
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

    private MdxQueryBean getMainQuery(QueryBean query, IEntityModel entityModel, String sourceName) {
        MdxQueryBean bean = new MdxQueryBean();
        bean.setName(query.getName());
        bean.setSourceName(sourceName);
        bean.setFilter(query.getFilter());
        bean.setCursor(query.getCursor());
        // groupBy 必须复制：addGroupField 会就地追加到列表上，直接透传会改写调用方的集合
        bean.setGroupBy(query.getGroupBy() == null ? null : new ArrayList<>(query.getGroupBy()));
        bean.setOrderBy(query.getOrderBy());
        bean.setLimit(query.getLimit());
        bean.setOffset(query.getOffset());
        bean.setDistinct(query.isDistinct());
        bean.setLeftJoinProps(query.getLeftJoinProps());
        bean.setDisableLogicalDelete(query.isDisableLogicalDelete());

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
            if (bean.hasGroupBy()) {
                // 具有group by，但是没有dimFields，则以group by的字段为dimFields
                dimFields = bean.getGroupBy().stream().map(GroupFieldBean::getName).collect(Collectors.toList());
            } else if (!agg) {
                // 没有聚合计算时才回退到主键维度
                dimFields = entityModel.getPkColumnNames();
            } else {
                // 所有字段都是聚合字段且没有 groupBy：这是一个全局聚合（只会产出一行），
                // 没有可对齐的维度。若回退到主键，addDimFields 会把主键列补进 SELECT 却不进
                // GROUP BY，生成 select count(x),t.id from t 这种非法 SQL。
                dimFields = Collections.emptyList();
            }
        } else if (agg) {
            // 显式指定的维度列会被 addDimFields 补进 SELECT，聚合查询里它们同时必须在 GROUP BY 中，
            // 否则生成的 SQL 非法（H2/PostgreSQL/MySQL ONLY_FULL_GROUP_BY 均直接报错）。
            for (String dimField : dimFields) {
                if (!containsGroupField(bean.getGroupBy(), dimField))
                    bean.addGroupField(dimField);
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
        if (isEmpty(dimFields))
            return fields;

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
        // 主表和子表的字段最终写入同一个结果map，而 QueryFieldBean.getLabel() 不含owner信息，
        // 同名/同聚合表达式会互相覆盖且没有任何提示。
        Set<String> labels = new HashSet<>();

        List<QueryFieldBean> fields = query.getFields();
        for (QueryFieldBean field : toNotNull(fields)) {
            checkFieldAttr(field);

            if (!StringHelper.isEmpty(field.getOwner())) {
                DaoQueryHelper.checkOwnerName(field.getOwner());
                // 具有owner的字段都认为是子表的字段，需要单独去获取
                subFieldsMap.computeIfAbsent(field.getOwner(), k -> new ArrayList<>()).add(field);
            } else {
                mainFields.add(field);
            }

            if (!labels.add(field.getLabel()))
                throw new NopException(ERR_ORM_QUERY_DUPLICATE_FIELD_LABEL).param(ARG_LABEL, field.getLabel());
        }
    }

    /**
     * expression/formula/aggregates 在 MDX 通道中没有任何消费方，SQL 里也不会出现。
     * 显式报错而不是静默丢弃，避免调用方以为自己做了字段级计算。
     */
    private void checkFieldAttr(QueryFieldBean field) {
        if (field.getExpression() != null)
            throw new NopException(ERR_ORM_QUERY_UNSUPPORTED_FIELD_ATTR).param(ARG_ATTR, "expression");

        if (field.getFormula() != null)
            throw new NopException(ERR_ORM_QUERY_UNSUPPORTED_FIELD_ATTR).param(ARG_ATTR, "formula");
    }

    private boolean containsGroupField(List<GroupFieldBean> groupBy, String name) {
        if (groupBy == null)
            return false;
        for (GroupFieldBean field : groupBy) {
            if (name.equals(field.getName()))
                return true;
        }
        return false;
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
            fields = fields.stream().map(QueryFieldBean::cloneExceptOwner).collect(Collectors.toList());
            List<String> dimFields = resolveSubDimFields(source, fields);
            MutableIntArray dimFieldIndexes = new MutableIntArray();
            query.setFields(addDimFields(fields, dimFields, dimFieldIndexes));
            query.setDimFields(dimFields);
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

    /**
     * 决定显式join子表的维度字段。三种来源按优先级排列：
     * <ol>
     *     <li>{@code source.dimFields}：调用方直接指定了子表维度列。</li>
     *     <li>{@code source.conditions}：由 {@link QueryBean#addJoin} 从 leftJoin/rightJoin/innerJoin 生成，
     *     按主表维度字段的顺序取出配对的右列，保证内存对齐时位置一一对应。</li>
     *     <li>回退为子表所有非聚合字段；子表字段全部是聚合字段时无法推导，必须由调用方声明。</li>
     * </ol>
     *
     * 返回值必须同时用于 addDimFields 和 {@code setDimFields}：前者决定结果集列的下标，
     * 后者决定 {@code MdxQueryExecutor} 回填过滤条件时下标对应的列名，两者不一致会导致子表数据无法对齐。
     */
    private List<String> resolveSubDimFields(QuerySourceBean source, List<QueryFieldBean> fields) {
        List<String> dimFields = source.getDimFields();
        if (!isEmpty(dimFields)) {
            return dimFields;
        }

        List<QueryJoinConditionBean> conditions = source.getConditions();
        if (!isEmpty(conditions)) {
            Map<String, String> rightFieldByLeft = new HashMap<>();
            for (QueryJoinConditionBean condition : conditions) {
                if (!isEmpty(condition.getLeftField()) && !isEmpty(condition.getRightField())) {
                    rightFieldByLeft.put(condition.getLeftField(), condition.getRightField());
                }
            }

            List<String> ret = new ArrayList<>(rightFieldByLeft.size());
            for (String mainDimField : mainQuery.getDimFields()) {
                String rightField = rightFieldByLeft.get(mainDimField);
                if (rightField == null) {
                    // join条件没有覆盖全部维度字段，无法在内存中按位置对齐
                    return null;
                }
                ret.add(rightField);
            }
            return ret;
        }

        dimFields = getNonAggFields(fields);
        if (!isEmpty(dimFields)) {
            return dimFields;
        }

        throw new NopException(ERR_ORM_QUERY_NO_DIM_FIELDS).param(ARG_SOURCE_NAME, source.getSourceName());
    }

    private MdxQueryBean getSubQuery(String alias, List<QueryFieldBean> fields, IEntityModel entityModel) {
        IEntityRelationModel rel = entityModel.getRelation(alias, false);
        MdxQueryBean query = new MdxQueryBean();
        query.setSourceName(rel.getRefEntityName());

        sourceNames.put(alias, query.getSourceName());

        List<String> mainDimFields = mainQuery.getDimFields();
        // 子表维度列必须与主表维度字段逐位对应，否则 buildDimValue 构造的位置键对不上，
        // 子表数据会静默丢失。因此这里按主表下标定位，而不是按 rel.getJoin() 的声明顺序追加
        // （rel.getJoin() 会被重排为关联目标实体的主键顺序，与主表主键顺序未必一致）。
        List<String> dimFields = new ArrayList<>(Collections.nCopies(mainDimFields.size(), null));

        for (IEntityJoinConditionModel join : rel.getJoin()) {
            if (join.getLeftProp() != null) {
                int idx = mainDimFields.indexOf(join.getLeftProp());
                // 如果字段不在关联字段范围之内，则认为这个字段会被汇总掉
                if (idx < 0)
                    continue;
                if (join.getRightProp() == null) {
                    throw new NopException(ERR_ORM_QUERY_INVALID_JOIN).param(ARG_COLLECTION_NAME,
                            rel.getCollectionName());
                }
                dimFields.set(idx, join.getRightProp());
            } else {
                query.addFilter(FilterBeans.eq(join.getRightProp(), join.getLeftValue()));
            }
        }

        for (int i = 0, n = dimFields.size(); i < n; i++) {
            if (dimFields.get(i) == null) {
                // 关联条件没有覆盖这个维度字段，退化为通过反向引用属性访问。
                // DaoQueryHelper 产出的 o.<refPropName>.<dimField> 会被 EQL 编译器
                // 展开成真实的 left join，因此这里的属性路径是合法的。
                dimFields.set(i, rel.getRefPropName() + "." + mainDimFields.get(i));
            }
        }

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