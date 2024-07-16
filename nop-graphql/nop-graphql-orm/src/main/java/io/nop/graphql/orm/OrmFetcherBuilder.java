/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.orm;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.biz.GraphQLQueryMethod;
import io.nop.graphql.core.biz.IBizObjectQueryProcessor;
import io.nop.graphql.core.biz.IBizObjectQueryProcessorBuilder;
import io.nop.graphql.core.fetcher.BeanPropertyFetcher;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.utils.GraphQLObjMetaHelper;
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
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.utils.OrmQueryHelper;
import io.nop.xlang.xdsl.ExtPropsGetter;
import io.nop.xlang.xmeta.IObjPropMeta;

import java.util.List;
import java.util.Set;

import static io.nop.graphql.core.GraphQLErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_PROP_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_NOT_SCALAR;
import static io.nop.graphql.orm.GraphQLOrmErrors.ERR_BIZ_CONNECTION_PROP_NOT_RELATION;

/**
 * 为ORM模型中定义的属性生成fetcher
 */
public class OrmFetcherBuilder {
    private final IOrmTemplate ormTemplate;
    private final IDaoProvider daoProvider;
    private final IBizObjectQueryProcessorBuilder queryProcessorBuilder;

    /**
     * 为减少创建fetcher，对于propId<=300的实体列，可以使用共享的fetcher
     */
    private final OrmEntityColumnFetcher[] cachedColumnFetchers = new OrmEntityColumnFetcher[300];

    public OrmFetcherBuilder(IOrmTemplate ormTemplate, IDaoProvider daoProvider,
                             IBizObjectQueryProcessorBuilder queryProcessorBuilder) {
        this.ormTemplate = ormTemplate;
        this.daoProvider = daoProvider;
        this.queryProcessorBuilder = queryProcessorBuilder;
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
            objDef.prependField(field);
        }
    }

    IDataFetcher getConnectionFetcher(IEntityModel entityModel, String objType, IObjPropMeta propMeta) {
        GraphQLQueryMethod queryMethod = GraphQLObjMetaHelper.getGraphQLQueryMethod(propMeta);
        String connectionProp = (String) propMeta.prop_get(GraphQLConstants.ATTR_GRAPHQL_CONNECTION_PROP);
        if (StringHelper.isEmpty(connectionProp) && queryMethod == null)
            return null;

        IEntityPropModel propModel = null;

        if (connectionProp != null) {
            propModel = entityModel.getProp(connectionProp, false);
            if (!propModel.isRelationModel()) {
                throw new NopException(ERR_BIZ_CONNECTION_PROP_NOT_RELATION).source(propMeta)
                        .param(ARG_BIZ_OBJ_NAME, objType).param(ARG_PROP_NAME, connectionProp);
            }
        }
        return buildConnectionFetcher(objType, queryMethod, (IEntityRelationModel) propModel, propMeta);
    }

    IDataFetcher buildConnectionFetcher(String objType, GraphQLQueryMethod queryMethod,
                                        IEntityRelationModel propModel, IObjPropMeta propMeta) {
        int maxFetchSize = ConvertHelper.toPrimitiveInt(propMeta.prop_get(GraphQLConstants.ATTR_GRAPHQL_MAX_FETCH_SIZE),
                -1, NopException::new);

        String bizObjName = GraphQLObjMetaHelper.getPropBizObjName(objType, propMeta, true);
        String authObjName = GraphQLObjMetaHelper.getPropAuthObjName(objType, propMeta);
        if (StringHelper.isEmpty(authObjName))
            authObjName = bizObjName;

        TreeBean filter = ExtPropsGetter.getTreeBean(propMeta, GraphQLConstants.TAG_GRAPHQL_FILTER);
        TreeBean relFilter = buildRelationFilter(propModel);
        if (filter != null) {
            filter = FilterBeans.and(filter, relFilter);
        } else {
            filter = relFilter;
        }

        List<OrderFieldBean> orderBy = ExtPropsGetter.getOrderBy(propMeta, GraphQLConstants.TAG_GRAPHQL_ORDER_BY);

        IBizObjectQueryProcessor<?> queryProcessor = queryProcessorBuilder.buildQueryProcessor(bizObjName);
        return new OrmEntityPropConnectionFetcher(queryProcessor, authObjName, maxFetchSize,
                queryMethod, filter, orderBy);
    }

    private TreeBean buildRelationFilter(IEntityRelationModel propModel) {
        return OrmQueryHelper.buildRelationFilter(propModel, joinProp -> OrmConstants.VALUE_PREFIX_PROP_REF + joinProp);
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

            fetcher = getColumnFetcher(propModel.getColumnPropId());
        } else if (propModel.isToOneRelation()) {
            fetcher = new OrmEntityRefFetcher(ormTemplate, name);
        } else if (propModel.isToManyRelation()) {
            fetcher = new OrmEntitySetFetcher(ormTemplate, name);
        } else {
            fetcher = new OrmEntityPropertyFetcher(ormTemplate, name);
        }
        return fetcher;
    }

    private IDataFetcher getColumnFetcher(int propId) {
        if (propId < cachedColumnFetchers.length)
            return cachedColumnFetchers[propId];
        return new OrmEntityColumnFetcher(ormTemplate, propId);
    }
}