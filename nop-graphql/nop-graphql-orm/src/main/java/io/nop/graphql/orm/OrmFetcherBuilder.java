/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.orm;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.graphql.GraphQLConnection;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.convert.SysConverterRegistry;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.IGenericType;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.fetcher.BeanPropertyFetcher;
import io.nop.graphql.core.fetcher.ConvertTypeFetcher;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import io.nop.graphql.core.utils.GraphQLTypeHelper;
import io.nop.graphql.orm.fetcher.OrmDependsPropFetcher;
import io.nop.graphql.orm.fetcher.OrmEntityColumnFetcher;
import io.nop.graphql.orm.fetcher.OrmEntityIdFetcher;
import io.nop.graphql.orm.fetcher.OrmEntityPropConnectionFetcher;
import io.nop.graphql.orm.fetcher.OrmEntityPropertyFetcher;
import io.nop.graphql.orm.fetcher.OrmEntityRefFetcher;
import io.nop.graphql.orm.fetcher.OrmEntitySetFetcher;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.xlang.xdsl.ExtPropsGetter;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_NOT_SCALAR;

/**
 * 为ORM模型中定义的属性生成fetcher
 */
public class OrmFetcherBuilder {
    private final IOrmTemplate ormTemplate;
    private final IDaoProvider daoProvider;
    private final BiConsumer<QueryBean, IDataFetchingEnvironment> queryProcessor;

    /**
     * 为减少创建fetcher，对于propId<=300的实体列，可以使用共享的fetcher
     */
    private final OrmEntityColumnFetcher[] cachedColumnFetchers = new OrmEntityColumnFetcher[300];

    public OrmFetcherBuilder(IOrmTemplate ormTemplate, IDaoProvider daoProvider,
                             BiConsumer<QueryBean, IDataFetchingEnvironment> queryProcessor) {
        this.ormTemplate = ormTemplate;
        this.daoProvider = daoProvider;
        this.queryProcessor = queryProcessor;
        for (int i = 1, n = cachedColumnFetchers.length; i < n; i++) {
            cachedColumnFetchers[i] = new OrmEntityColumnFetcher(ormTemplate, i);
        }
    }

    public void initFetchers(GraphQLObjectDefinition objDef, String entityName) {
        IEntityModel entityModel = ormTemplate.getOrmModel().getEntityModel(entityName);
        if (entityModel == null)
            return;

        for (GraphQLFieldDefinition fieldDef : objDef.getFields()) {
            // 如果fetcher已经由biz或者java method提供，则跳过orm的处理
            if (fieldDef.getFetcher() != null && fieldDef.getFetcher() != BeanPropertyFetcher.INSTANCE)
                continue;

            String name = fieldDef.getName();
            IObjPropMeta propMeta = fieldDef.getPropMeta();

            if (propMeta != null) {
                IDataFetcher fetcher = getConnectionFetcher(entityModel, objDef.getName(), propMeta);
                if (fetcher != null) {
                    fieldDef.setFetcher(fetcher);
                    continue;
                }
            }

            Set<String> dependsOn = null;
            String mapToProp = null;
            if (propMeta != null) {
                mapToProp = propMeta.getMapToProp();
                dependsOn = propMeta.getDepends();
            }

            IDataFetcher fetcher;
            if (mapToProp != null) {
                fetcher = buildPropFetcher(dependsOn, mapToProp);
            } else if (dependsOn != null) {
                fetcher = buildPropFetcher(dependsOn, fieldDef.getName());
            } else {
                fetcher = buildFetcher(entityModel, name, objDef, fieldDef);
                if (fetcher == null)
                    continue;
            }

            fieldDef.setFetcher(fetcher);
        }

        if (objDef.getField(GraphQLConstants.PROP_ID) == null) {
            GraphQLFieldDefinition field = new GraphQLFieldDefinition();
            field.setName(GraphQLConstants.PROP_ID);
            field.setType(GraphQLTypeHelper.scalarType(GraphQLScalarType.ID));
            field.setFetcher(OrmEntityIdFetcher.INSTANCE);
            objDef.addField(field);
        }
    }

    IDataFetcher getConnectionFetcher(IEntityModel entityModel, String objType, IObjPropMeta propMeta) {
        String connectionProp = (String) propMeta.prop_get(GraphQLConstants.ATTR_GRAPHQL_CONNECTION_PROP);
        if (StringHelper.isEmpty(connectionProp))
            return null;

        IEntityPropModel propModel = entityModel.getProp(connectionProp, false);
        if (!propModel.isToManyRelation()) {
            return null;
        }
        return buildConnectionFetcher(objType, (IEntityRelationModel) propModel, propMeta);
    }

    IDataFetcher buildConnectionFetcher(String objType, IEntityRelationModel propModel, IObjPropMeta propMeta) {
        IEntityDao dao = daoProvider.dao(propModel.getRefEntityName());
        int maxFetchSize = ConvertHelper.toPrimitiveInt(propMeta.prop_get(GraphQLConstants.ATTR_GRAPHQL_MAX_FETCH_SIZE),
                -1, NopException::new);
        String bizObjName = propMeta.getBizObjName();
        if (bizObjName == null) {
            bizObjName = StringHelper.simpleClassName(propModel.getRefEntityName());
        }
        String fetchAction = GraphQLNameHelper.getFetchAction(objType, propMeta.getName());

        TreeBean filter = ExtPropsGetter.getTreeBean(propMeta, GraphQLConstants.TAG_GRAPHQL_FILTER);
        TreeBean relFilter = buildRelationFilter(propModel);
        if (filter != null) {
            filter = FilterBeans.and(filter, relFilter);
        } else {
            filter = relFilter;
        }

        List<OrderFieldBean> orderBy = ExtPropsGetter.getOrderBy(propMeta, GraphQLConstants.TAG_GRAPHQL_ORDER_BY);

        boolean findFirst = isFindFirst(propMeta);

        return new OrmEntityPropConnectionFetcher(dao, bizObjName, fetchAction, maxFetchSize, findFirst, filter,
                orderBy, queryProcessor);
    }

    private TreeBean buildRelationFilter(IEntityRelationModel propModel) {
        List<TreeBean> filters = new ArrayList<>(propModel.getJoin().size());
        for (IEntityJoinConditionModel join : propModel.getJoin()) {
            if (join.getRightPropModel() != null) {
                if (join.getLeftProp() != null) {
                    filters.add(FilterBeans.eq(join.getRightProp(), OrmConstants.VALUE_PREFIX_PROP_REF + join.getLeftProp()));
                } else {
                    filters.add(FilterBeans.eq(join.getRightProp(), join.getRightValue()));
                }
            }
        }
        return FilterBeans.and(filters);
    }

    boolean isFindFirst(IObjPropMeta propMeta) {
        ISchema schema = propMeta.getSchema();
        if (schema == null)
            return false;
        IGenericType type = schema.getType();
        return type.getRawClass() != GraphQLConnection.class;
    }

    IDataFetcher buildPropFetcher(Set<String> dependsOn, String propName) {
        return new OrmDependsPropFetcher(ormTemplate, dependsOn, propName);
    }

    private IDataFetcher buildFetcher(IEntityModel entityModel, String name, GraphQLObjectDefinition objDef,
                                      GraphQLFieldDefinition fieldDef) {
        IEntityPropModel propModel = entityModel.getProp(name, true);
        if (propModel == null)
            return null;

        if (propModel.getName().equals(OrmConstants.PROP_ID)) {
            return OrmEntityIdFetcher.INSTANCE;
        }

        IDataFetcher fetcher;
        if (propModel.isColumnModel()) {
            GraphQLScalarType scalarType = fieldDef.getType().getScalarType();
            if (scalarType == null)
                throw new NopException(ERR_GRAPHQL_FIELD_NOT_SCALAR).source(fieldDef)
                        .param(ARG_OBJ_NAME, objDef.getName()).param(ARG_FIELD_NAME, fieldDef.getName());

            fetcher = buildColumnFetcher((IColumnModel) propModel, scalarType);
        } else if (propModel.isToOneRelation()) {
            fetcher = new OrmEntityRefFetcher(ormTemplate, name);
        } else if (propModel.isToManyRelation()) {
            fetcher = new OrmEntitySetFetcher(ormTemplate, name);
        } else {
            fetcher = new OrmEntityPropertyFetcher(ormTemplate, name);
        }
        return fetcher;
    }

    private IDataFetcher buildColumnFetcher(IColumnModel col, GraphQLScalarType type) {
        StdDataType colType = col.getStdDataType();
        StdDataType fieldType = type.getStdDataType();
        IDataFetcher fetcher = getColumnFetcher(col.getPropId());
        if (colType != fieldType) {
            ITypeConverter converter = SysConverterRegistry.instance().getConverterByType(fieldType.getJavaClass());
            fetcher = new ConvertTypeFetcher(converter, fetcher);
        }
        return fetcher;
    }

    private IDataFetcher getColumnFetcher(int propId) {
        if (propId < cachedColumnFetchers.length)
            return cachedColumnFetchers[propId];
        return new OrmEntityColumnFetcher(ormTemplate, propId);
    }
}