/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core;

import io.nop.api.core.ApiConstants;

public interface GraphQLConstants {
    String API_IMP_MODEL_PATH = "/nop/graphql/imp/api.imp.xml";
    String XDSL_SCHEMA_API = "/nop/schema/api.xdef";
    String FILE_TYPE_API_XLSX = "api.xlsx";
    String DSL_TYPE_API = "api";
    String FILE_TYPE_API_XML = "api.xml";

    String TYPE_QUERY = "Query";
    String TYPE_MUTATION = "Mutation";
    String TYPE_SUBSCRIPTION = "Subscription";

    String ATTR_GRAPHQL_DICT_VALUE_PROP = "graphql:dictValueProp";
    String ATTR_GRAPHQL_DICT_NAME = "graphql:dictName";
    String ATTR_GRAPHQL_PROP = "graphql:prop";
    String ATTR_GRAPHQL_MAX_FETCH_SIZE = "graphql:maxFetchSize";

    String ATTR_GRAPHQL_CONNECTION_PROP = "graphql:connectionProp";

    String ATTR_GRAPHQL_QUERY_METHOD = "graphql:queryMethod";

    String ATTR_GRAPHQL_TYPE = "graphql:type";

    String ATTR_GRAPHQL_INPUT_TYPE = "graphql:inputType";

    String ATTR_GRAPHQL_AUTH_OBJ_NAME = "graphql:authObjName";

    String TAG_GRAPHQL_FILTER = "graphql:filter";
    String TAG_GRAPHQL_ORDER_BY = "graphql:orderBy";

    String TAG_GRAPHQL_TRANS_FILTER = "graphql:transFilter";

    String PROP_TAG_CONNECTION = "connection";

    String POSTFIX_CONNECTION = "Connection";
    String PROP_TAG_NOT_RETURN = "not-return";

    String DIRECTIVE_DICT_LABEL = "DictLabel";

    String DIRECTIVE_TREE_CHILDREN = "TreeChildren";

    String DIRECTIVE_DEPENDS_ON = "DependsOn";

    String DIRECTIVE_MAP_TO_PROP = "MapToProp";

    String DIRECTIVE_AUTH = "Auth";

    String DIRECTIVE_MASK = "Mask";

    String DIRECTIVE_LABEL = "Label";

    String ARG_ID = "id";
    String ARG_IDS = "ids";
    String ARG_QUERY = "query";

    String ARG_FILTER = "filter";

    String PROP_ID = "id";

    /**
     * GraphQL通过__typename属性返回实体对象名
     */
    String PROP___TYPENAME = "__typename";

    String POSTFIX_BIZ_MODEL = "BizModel";

    /**
     * GraphQL的顶层操作名格式为bizObjName + '__' + bizAction，例如
     *
     * <pre>{@code
     * type Query{
     *     NopAuthUser__get(id:String): NopAuthUser
     * }
     * }</pre>
     * <p>
     * NopAuthUser为bizObjName，而bizAction为get
     */
    String OBJ_ACTION_SEPARATOR = "__";

    String PAGE_BEAN_PREFIX = "PageBean_";

    String GRAPHQL_CONNECTION_PREFIX = "GraphQLConnection_";
    String PAGE_BEAN = "PageBean";

    String GRAPHQL_CONNECTION = "GraphQLConnection";

    String FIELD_ITEMS = "items";

    String FIELD_EDGES = "edges";
    String FIELD_TOTAL = "total";

    String FIELD_PAGE_INFO = "pageInfo";

    String FIELD_NEXT_CURSOR = "nextCursor";

    String BIZ_OBJ_NAME_THIS_OBJ = "THIS_OBJ";

    String BIZ_OBJ_NAME_ROOT = "ROOT";

    String DIRECTIVE_INCLUDE = "include";
    String DIRECTIVE_SKIP = "skip";

    String DIRECTIVE_ARG_IF = "if";

    String DIRECTIVE_DEPRECATED = "deprecated";
    String DIRECTIVE_ARG_REASON = "reason";

    String DIRECTIVE_ARG_MAX = "max";

    String VAR_THIS_OBJ = "thisObj";

    /**
     * 系统参数以@为前缀
     */
    String SYS_PARAM_PREFIX = "@";

    String SYS_PARAM_SELECTION = ApiConstants.SYS_PARAM_SELECTION;
    String SYS_PARAM_ARGS = "@args";

    String PARAM_PATH = "path";

    String VAR_ENTITY = "entity";
    String VAR_VALUE = "value";


    String SUB_PARAMS_PREFIX = "_subArgs.";
    String FILTER_PREFIX = "filter_";

    String QUERY_ORDER_BY_KEY = "query_orderBy";

    String PROP_ORDER_BY = "orderBy";
    String PROP_FILTER = "filter";

    String V_PREFIX = "v_";

    String SPECIAL_VALUE_EMPTY = "__empty";

    String SPECIAL_VALUE_NULL = "__null";

    String FRAGMENT_SELECTION_PREFIX = "F_";

    String FRAGMENT_DEFAULTS = "F_defaults";
}