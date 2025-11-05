package io.nop.graphql.core.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.utils.BeanReflectHelper;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.biz.GraphQLQueryMethod;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.xlang.xmeta.IObjPropMeta;

import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.graphql.core.GraphQLConstants.ATTR_GRAPHQL_QUERY_METHOD;
import static io.nop.graphql.core.GraphQLErrors.ARG_PROP_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_BIZ_PROP_NO_BIZ_OBJ_NAME_ATTR;

public class GraphQLObjMetaHelper {

    public static String getPropGraphQLType(String bizObjName, IObjPropMeta propMeta) {
        String gqlType = (String) propMeta.prop_get(GraphQLConstants.ATTR_GRAPHQL_TYPE);
        if (!StringHelper.isEmpty(gqlType))
            return gqlType;

        GraphQLQueryMethod queryMethod = getGraphQLQueryMethod(propMeta);
        if (queryMethod == null)
            return null;

        if (queryMethod == GraphQLQueryMethod.findCount)
            return GraphQLScalarType.Long.name();

        String propBizObjName = getPropBizObjName(bizObjName, propMeta, true);

        if (queryMethod == GraphQLQueryMethod.findFirst) {
            return propBizObjName;
        } else if (queryMethod == GraphQLQueryMethod.findList) {
            return "[" + propBizObjName + "]";
        } else if (queryMethod == GraphQLQueryMethod.findPage) {
            return GraphQLConstants.PAGE_BEAN_PREFIX + propBizObjName;
        } else {
            return GraphQLConstants.GRAPHQL_CONNECTION_PREFIX + propBizObjName;
        }
    }

    public static GraphQLQueryMethod getGraphQLQueryMethod(IObjPropMeta propMeta) {
        try {
            return BeanReflectHelper.castValueByFactoryMethod(GraphQLQueryMethod.class, propMeta.prop_get(ATTR_GRAPHQL_QUERY_METHOD));
        } catch (NopException e) {
            e.source(propMeta);
            throw e;
        }
    }

    public static String getPropBizObjName(String bizObjName, IObjPropMeta propMeta, boolean mandatory) {
        String propBizObjName = propMeta.getBizObjName();
        if (propBizObjName == null)
            propBizObjName = propMeta.getItemBizObjName();

        if (GraphQLConstants.BIZ_OBJ_NAME_THIS_OBJ.equals(propBizObjName))
            propBizObjName = bizObjName;

        if (propBizObjName == null && mandatory)
            throw new NopException(ERR_BIZ_PROP_NO_BIZ_OBJ_NAME_ATTR)
                    .source(propMeta).param(ARG_BIZ_OBJ_NAME, bizObjName).param(ARG_PROP_NAME, propMeta.getName());
        return propBizObjName;
    }

    public static String getPropAuthObjName(String bizObjName, IObjPropMeta propMeta) {
        return (String) propMeta.prop_get(GraphQLConstants.ATTR_GRAPHQL_AUTH_OBJ_NAME);
    }

}
