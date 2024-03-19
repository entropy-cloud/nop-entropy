/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz;

import io.nop.core.CoreConstants;
import io.nop.graphql.core.GraphQLConstants;

import static io.nop.api.core.util.IOrdered.NORMAL_PRIORITY;

public interface BizConstants {
    String XDEF_BIZ = "/nop/schema/biz/xbiz.xdef";

    String FILTER_PREFIX = "filter_";
    String V_PREFIX = "v_";

    String ARG_QUERY = "query";
    String PROP_FILTER = "filter";

    String SPECIAL_VALUE_EMPTY = "__empty";

    String SPECIAL_VALUE_NULL = "__null";

    String BEAN_nopQueryBeanArgsNormalizer = "nopQueryBeanArgsNormalizer";

    String FILE_POSTFIX_XMETA = ".xmeta";

    String XLIB_NS_BIZ = "biz";

    String XLIB_BIZ_PATH = "/nop/biz/xlib/biz.xlib";
    String XLIB_BIZ_CHECK_PATH = "/nop/biz/xlib/biz!check.xlib";
    String XLIb_BIZ_FILTER_PATH = "/nop/biz/xlib/biz!filter.xlib";

    String VAR_USER_CONTEXT = "userContext";


    String FILE_EXT_XBIZ = "xbiz";
    String DEFAULT_WORK_EXECUTOR_NAME = "defaultWorkExecutor";

    String ATTR_REQUEST = "request";

    String ATTR_REQUEST_HEADERS = "requestHeaders";

    int CACHE_DECORATOR_PRIORITY = NORMAL_PRIORITY - 100;

    int TRANSACTION_DECORATOR_PRIORITY = NORMAL_PRIORITY - 50;

    String BIZ_ACTION_TYPE_MUTATION = "mutation";
    String BIZ_ACTION_TYPE_QUERY = "query";

    String METHOD_FIND_PAGE = "findPage";
    String METHOD_FIND_FIRST = "findFirst";
    String METHOD_FIND_LIST = "findList";

    String METHOD_FIND_COUNT = "findCount";
    String METHOD_GET = "get";
    String METHOD_SAVE = "save";
    String METHOD_UPDATE = "update";
    String METHOD_DELETE = "delete";
    String METHOD_BATCH_MODIFY = "batchModify";

    String METHOD_TRY_SAVE = "trySave";
    String METHOD_TRY_UPDATE = "tryUpdate";
    String METHOD_TRY_DELETE = "tryDelete";

    String METHOD_AS_DICT = "asDict";

    String VAR_DATA = "data";
    String VAR_ROOT = "root";
    String VAR_VALUE = "value";
    String VAR_TRNAS_DATA = "transData";
    String VAR_ENTITY = "entity";

    String VAR_PROP_META = "propMeta";

    String VAR_OBJ_META = "objMeta";

    String OBJ_DICT_PREFIX = "obj/";
    String TAG_DICT = "dict";

    String EXT_MAX_PAGE_SIZE = "ext:maxPageSize";
    String EXT_RELATION = "ext:relation";
    String EXT_JOIN_LEFT_PROP = "ext:joinLeftProp";
    String EXT_JOIN_RIGHT_PROP = "ext:joinRightProp";
    String EXT_KIND = "ext:kind";

    String ORM_MANY_TO_MANY_REF_PROP = "orm:manyToManyRefProp";

    String EXT_KIND_VALUE_COMPONENT = "component";

    String GRAPHQL_BASE_NAME = "graphql:base";
    String BASE_CRUD = "crud";

    /**
     * 标记需要被递归删除的关联表
     */
    String TAG_CASCADE_DELETE = "cascade-delete";

    String TAG_PARENT = "parent";

    /**
     * 标记在关联属性上，表示关联表上的字段是否自动可查询
     */
    String TAG_QUERYABLE = "queryable";

    /**
     * 标记在关联属性上，表示关联表上的字段是否自动可排序
     */
    String TAG_SORTABLE = "sortable";


    String PROP_KIND_TO_ONE = "to-one";
    String PROP_KIND_TO_MANY = "to-many";

    String BIZ_OBJ_NAME_THIS_OBJ = GraphQLConstants.BIZ_OBJ_NAME_THIS_OBJ;

    String ACTION_ARG_THIS_OBJ = "thisObj";
    String ACTION_ARG_ENTITY = "entity";
    String ACTION_ARG_SVC_CONTEXT = CoreConstants.VAR_SVC_CTX;
    String ACTION_ARG_INVOCATION = "invocation";

    String GRAPHQL_EXTENSION_MAKER_CHECKER_TRY_RESPONSE = "nop.maker-checker.try-response";

    String SELECTION_COPY_FOR_NEW = "copyForNew";

    String PARAM_IGNORE_UNKNOWN = "ignoreUnknown";

    String TEMP_BIZ_OBJ_ID = "__TEMP__";
}
