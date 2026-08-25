/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.GroupFieldBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.sql.FilterBeanToSQLTransformer;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_FUNC_NAME;
import static io.nop.orm.OrmErrors.ARG_OWNER;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_PATH;
import static io.nop.orm.OrmErrors.ERR_ORM_CURSOR_ORDER_BY_NOT_COLUMN;
import static io.nop.orm.OrmErrors.ERR_ORM_CURSOR_SORT_VALUE_NULL;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_FIELD_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_FUNC_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_OWNER_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_EXAMPLE_PROP_NOT_INITED;

/**
 * 提供根据Query信息拼接SQL语句的帮助函数
 */
public class DaoQueryHelper {
    public static TreeBean buildFilterFromExample(IOrmEntity entity) {
        if (!entity.orm_inited())
            throw new NopException(ERR_ORM_QUERY_EXAMPLE_PROP_NOT_INITED).param(ARG_ENTITY_NAME,
                    entity.orm_entityName());

        List<TreeBean> filters = new ArrayList<>();

        entity.orm_forEachInitedProp((v, propId) -> {
            String propName = entity.orm_propName(propId);
            filters.add(FilterBeans.eq(propName, v));
        });

        return FilterBeans.and(filters);
    }

    public static QueryBean buildQueryFromExample(IOrmEntity entity) {
        QueryBean query = new QueryBean();
        query.setFilter(buildFilterFromExample(entity));
        return query;
    }

    public static List<OrderFieldBean> buildPkOrderBy(IEntityModel entityModel) {
        return entityModel.getPkColumns().stream().map(col -> OrderFieldBean.forField(col.getName()))
                .collect(Collectors.toList());
    }

    public static SQL queryToSelectObjectSql(String entityName, QueryBean query) {
        SQL.SqlBuilder sb = newSQL(query);
        sb.append("select o from ").append(entityName).as("o");
        if (query.getLeftJoinProps() != null) {
            for (String propName : query.getLeftJoinProps()) {
                Guard.checkArgument(StringHelper.isValidPropPath(propName));
                sb.append(" left join o.").append(propName);
            }
        }
        if (query != null) {
            appendWhere(sb, "o", query.getFilter());

            appendOrderBy(sb, "o", query.getOrderBy());
        }
        return sb.end();
    }

    public static boolean appendWhere(SQL.SqlBuilder sb, String owner, ITreeBean filter) {
        if (filter != null) {
            sb.br().where();
            appendFilter(sb, owner, filter);
            return true;
        }
        return false;
    }

    public static void checkFuncName(String funcName) {
        if (!StringHelper.isValidSimpleVarName(funcName))
            throw new NopException(ERR_ORM_INVALID_FUNC_NAME).param(ARG_FUNC_NAME, funcName);
    }

    public static void checkFieldName(String name) {
        if (!StringHelper.isValidPropPath(name))
            throw new NopException(ERR_ORM_INVALID_FIELD_NAME).param(ARG_PROP_PATH, name);
    }

    public static void checkOwnerName(String name) {
        if (name == null)
            return;

        if (!StringHelper.isValidSimpleVarName(name))
            throw new NopException(ERR_ORM_INVALID_OWNER_NAME).param(ARG_OWNER, name);
    }

    public static void checkEntityName(String name) {
        if (!StringHelper.isValidClassName(name))
            throw new NopException(ERR_ORM_INVALID_ENTITY_NAME).param(ARG_ENTITY_NAME, name);
    }

    public static SQL.SqlBuilder queryToSelectFieldsSql(QueryBean query, String delFlagProp) {
        if (query == null)
            throw new IllegalArgumentException("null query");

        SQL.SqlBuilder sb = newSQL(query);
        sb.select();
        if(query.isDistinct()){
            sb.distinct();
        }

        for (QueryFieldBean field : query.getFields()) {
            if (StringHelper.isEmpty(field.getAggFunc())) {
                appendField(sb, field.getOwner(), field.getName());
            } else {
                checkFuncName(field.getAggFunc());
                sb.append(field.getAggFunc()).append('(');
                appendField(sb, field.getOwner(), field.getName());
                sb.append(')');
            }
            sb.append(',');
        }
        sb.deleteTail(1);

        checkEntityName(query.getSourceName());
        sb.from().append(query.getSourceName()).as("o");

        if (query.getLeftJoinProps() != null) {
            for (String propName : query.getLeftJoinProps()) {
                Guard.checkArgument(StringHelper.isValidPropPath(propName));
                sb.append(" left join o.").append(propName);
            }
        }

        appendWhere(sb, "o", query.getFilter());
        appendGroupBy(sb, "o", query.getGroupBy());
        appendOrderBy(sb, "o", query.getOrderBy());

        return sb;
    }

    private static void appendField(SQL.SqlBuilder sb, String ownerName, String name) {
        // 与appendGroupBy/appendOrderBy的防御保持一致，避免非法的owner/字段名被拼接到SQL中
        checkOwnerName(ownerName);
        checkFieldName(name);
        if (ownerName == null)
            ownerName = "o";
        if (name.equals("o")) {
            sb.append(ownerName);
        } else {
            sb.append(ownerName).append('.').append(name);
        }
    }

    private static SQL.SqlBuilder newSQL(QueryBean query) {
        SQL.SqlBuilder sb = SQL.begin();
        if (query != null) {
            sb.name(query.getName());
            if (query.getTimeout() != null)
                sb.timeout(query.getTimeout());
            if (query.isDisableLogicalDelete()) {
                sb.disableLogicalDelete();
            }
        }
        return sb;
    }

    public static void appendGroupBy(SQL.SqlBuilder sb, String defaultOwner, List<GroupFieldBean> groupBy) {
        if (groupBy == null || groupBy.isEmpty())
            return;

        sb.br().groupBy();
        for (int i = 0, n = groupBy.size(); i < n; i++) {
            GroupFieldBean groupField = groupBy.get(i);
            if (i != 0)
                sb.append(',');
            checkOwnerName(groupField.getOwner());
            checkFieldName(groupField.getName());
            String owner = groupField.getOwner();
            if (owner == null)
                owner = defaultOwner;
            sb.owner(owner).append(groupField.getName());
        }
    }

    public static void appendFilter(SQL.SqlBuilder sb, String defaultOwner, ITreeBean filter) {
        new FilterBeanToSQLTransformer(sb, true, defaultOwner).visitRoot(filter, DisabledEvalScope.INSTANCE);
    }

    public static void appendOrderBy(SQL.SqlBuilder sb, String defaultOwner, List<OrderFieldBean> orderBy) {
        if (orderBy == null || orderBy.isEmpty())
            return;

        sb.br().orderBy();
        for (int i = 0, n = orderBy.size(); i < n; i++) {
            OrderFieldBean orderField = orderBy.get(i);
            if (i != 0)
                sb.append(',');
            checkOwnerName(orderField.getOwner());
            checkFieldName(orderField.getName());
            sb.orderField(defaultOwner, orderField);
        }
    }

    public static void appendReverseOrderBy(SQL.SqlBuilder sb, String defaultOwner, List<OrderFieldBean> orderBy) {
        if (orderBy == null || orderBy.isEmpty())
            return;

        sb.br().orderBy();
        for (int i = 0, n = orderBy.size(); i < n; i++) {
            OrderFieldBean orderField = orderBy.get(i);
            if (i != 0)
                sb.append(',');
            checkOwnerName(orderField.getOwner());
            checkFieldName(orderField.getName());
            sb.reverseOrderField(defaultOwner, orderField);
        }
    }

    public static SQL queryToCountSql(String entityName, QueryBean query) {
        SQL.SqlBuilder sb = newSQL(query);

        sb.append("select count(1) from ").append(entityName).as("o");
        if (query.getLeftJoinProps() != null) {
            for (String propName : query.getLeftJoinProps()) {
                Guard.checkArgument(StringHelper.isValidPropPath(propName));
                sb.append(" left join o.").append(propName);
            }
        }
        appendWhere(sb, "o", query == null ? null : query.getFilter());
        return sb.end();
    }

    public static SQL queryToDeleteSql(String entityName, QueryBean query) {
        SQL.SqlBuilder sb = newSQL(query);

        sb.deleteFrom(entityName);
        appendWhere(sb, null, query == null ? null : query.getFilter());
        return sb.end();
    }

    public static SQL queryToUpdateSql(String entityName, QueryBean query, Map<String, Object> props) {
        SQL.SqlBuilder sb = newSQL(query);
        checkEntityName(entityName);
        sb.update(entityName);
        // props的key会被原样拼接到set子句的SQL文本中，必须做合法性校验，
        // 避免恶意字段名(如带注释符)被拼入后吞掉参数占位符
        if (props != null) {
            for (String name : props.keySet()) {
                checkFieldName(name);
            }
        }
        sb.br().set(null, props);
        if (query != null) {
            TreeBean filter = query.getFilter();
            if (filter != null) {
                sb.br().where();
                appendFilter(sb, null, filter);
            }
        }
        return sb.end();
    }

    public static <T extends IOrmEntity> SQL queryToFindNextSql(IEntityModel entityModel, T lastEntity,
                                                                ITreeBean filter, List<OrderFieldBean> orderBy, String delFlagProp) {
        List<OrderFieldBean> orderByPk = appendPkOrderBy(entityModel, orderBy);

        SQL.SqlBuilder sb = SQL.begin();
        sb.append("select o from ").append(entityModel.getName()).as("o");
        boolean hasCond = appendWhere(sb, "o", filter);
        if (lastEntity != null) {
            if (hasCond) {
                sb.and();
            }else{
                sb.where();
            }
            appendCursorCondition(sb, entityModel, lastEntity, orderByPk, true);
        }

        appendOrderBy(sb, "o", orderByPk);
        return sb.end();
    }

    public static <T extends IOrmEntity> SQL queryToFindPrevSql(IEntityModel entityModel, T cursorEntity,
                                                                ITreeBean filter, List<OrderFieldBean> orderBy, String delFlagProp) {
        List<OrderFieldBean> orderByPk = appendPkOrderBy(entityModel, orderBy);

        SQL.SqlBuilder sb = SQL.begin();
        sb.append("select o from ").append(entityModel.getName()).as("o");
        boolean hasCond = appendWhere(sb, "o", filter);
        if (cursorEntity != null) {
            if (hasCond) {
                sb.and();
            }else{
                sb.where();
            }
            appendCursorCondition(sb, entityModel, cursorEntity, orderByPk, false);
        }

        appendReverseOrderBy(sb, "o", orderByPk);
        return sb.end();
    }

    static boolean hasField(List<OrderFieldBean> orderBy, String name) {
        if (orderBy == null)
            return false;
        return orderBy.stream().anyMatch(f -> f.getName().equals(name));
    }

    static List<OrderFieldBean> appendPkOrderBy(IEntityModel entityModel, List<OrderFieldBean> orderBy) {
        List<OrderFieldBean> orderByPk = new ArrayList<>();
        if (orderBy != null) {
            orderByPk.addAll(orderBy);
        }
        for (IColumnModel col : entityModel.getPkColumns()) {
            if (!hasField(orderBy, col.getName())) {
                orderByPk.add(OrderFieldBean.forField(col.getName()));
            }
        }
        return orderByPk;
    }

    /**
     * 按完整排序键(orderByPk)构造 keyset 游标条件。next=true 时生成"排序位置严格在游标之后"的条件，
     * 否则生成"严格在游标之前"的条件。单个排序字段时退化为简单比较；
     * 多个排序字段时按 (f1 cmp v1 or (f1 = v1 and f2 cmp v2) or ...) 展开，
     * 不使用部分方言(如SQL Server)不支持的行值比较语法
     */
    static void appendCursorCondition(SQL.SqlBuilder sb, IEntityModel entityModel, IOrmEntity entity,
                                      List<OrderFieldBean> orderByPk, boolean next) {
        int n = orderByPk.size();
        if (n == 1) {
            appendCursorCmp(sb, entityModel, entity, orderByPk.get(0), next);
            return;
        }

        sb.append('(');
        for (int i = 0; i < n; i++) {
            if (i != 0)
                sb.append(" or ");
            sb.append('(');
            for (int j = 0; j < i; j++) {
                if (j != 0)
                    sb.append(" and ");
                appendCursorEq(sb, entityModel, entity, orderByPk.get(j));
            }
            if (i != 0)
                sb.append(" and ");
            appendCursorCmp(sb, entityModel, entity, orderByPk.get(i), next);
            sb.append(')');
        }
        sb.append(')');
    }

    private static void appendCursorEq(SQL.SqlBuilder sb, IEntityModel entityModel, IOrmEntity entity,
                                       OrderFieldBean field) {
        IColumnModel col = requireOrderColumn(entityModel, field);
        Object value = entity.orm_propValue(col.getPropId());
        if (value == null) {
            sb.owner("o").isNull(col.getName());
        } else {
            sb.owner("o").eq(col.getName(), value);
        }
    }

    private static void appendCursorCmp(SQL.SqlBuilder sb, IEntityModel entityModel, IOrmEntity entity,
                                        OrderFieldBean field, boolean next) {
        IColumnModel col = requireOrderColumn(entityModel, field);
        Object value = entity.orm_propValue(col.getPropId());
        if (value == null)
            throw new NopException(ERR_ORM_CURSOR_SORT_VALUE_NULL).param(ARG_PROP_NAME, field.getName())
                    .param(ARG_ENTITY_NAME, entityModel.getName());

        // 升序字段: next为>、prev为<；降序字段相反
        boolean greater = next == !field.isDesc();
        if (greater) {
            sb.owner("o").gt(col.getName(), value);
        } else {
            sb.owner("o").lt(col.getName(), value);
        }
    }

    /**
     * 游标条件只能基于实体自身的简单列属性构造。排序字段带其他 owner 或不是实体列属性时无法生成正确的 keyset 条件，
     * 显式报错而不是退化为只按主键比较导致跳行/漏行
     */
    private static IColumnModel requireOrderColumn(IEntityModel entityModel, OrderFieldBean field) {
        if (!StringHelper.isEmpty(field.getOwner()) && !"o".equals(field.getOwner()))
            throw new NopException(ERR_ORM_CURSOR_ORDER_BY_NOT_COLUMN).param(ARG_PROP_NAME, field.toString())
                    .param(ARG_ENTITY_NAME, entityModel.getName());

        IColumnModel col = entityModel.getColumn(field.getName(), true);
        if (col == null)
            throw new NopException(ERR_ORM_CURSOR_ORDER_BY_NOT_COLUMN).param(ARG_PROP_NAME, field.toString())
                    .param(ARG_ENTITY_NAME, entityModel.getName());
        return col;
    }

    /**
     * 查找所有具有指定属性值的实体。例如 buildPropsFilterForList(list,["a","b"]) 对应于查询 (a=xx and b=yy or ...)
     *
     * @param list      对象列表，从中取到它每个条目的属性值作为查询条件。
     * @param propNames 作为查询条件的属性名
     */
    public static TreeBean buildPropsFilterForList(List<?> list, List<String> propNames) {
        List<TreeBean> filters = new ArrayList<>(list.size());

        for (Object obj : list) {
            if (propNames.size() == 1) {
                String propName = propNames.get(0);
                filters.add(FilterBeans.eq(propName, BeanTool.getComplexProperty(obj, propName)));
            } else {
                filters.add(getPropFilter(obj, propNames));
            }
        }
        return FilterBeans.or(filters);
    }

    private static TreeBean getPropFilter(Object obj, List<String> propNames) {
        List<TreeBean> filters = new ArrayList<>(propNames.size());
        for (String propName : propNames) {
            filters.add(FilterBeans.eq(propName, BeanTool.getComplexProperty(obj, propName)));
        }
        return FilterBeans.and(filters);
    }

}
