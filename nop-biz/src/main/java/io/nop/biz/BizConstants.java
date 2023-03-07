/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz;

import io.nop.core.CoreConstants;
import io.nop.graphql.core.GraphQLConstants;

import static io.nop.api.core.util.IOrdered.NORMAL_PRIORITY;

public interface BizConstants {
    String XDEF_BIZ = "/nop/schema/biz/xbiz.xdef";

    String FILE_POSTFIX_XMETA = ".xmeta";

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

    String OBJ_DICT_PREFIX = "obj/";
    String TAG_DICT = "dict";

    String EXT_MAX_PAGE_SIZE = "ext:maxPageSize";
    String EXT_RELATION = "ext:relation";
    String EXT_CONTROL = "ext:control";
    String EXT_JOIN_LEFT_PROP = "ext:joinLeftProp";
    String EXT_JOIN_RIGHT_PROP = "ext:joinRightProp";
    String EXT_KIND = "ext:kind";

    String EXT_KIND_VALUE_COMPONENT = "component";

    /**
     * 标记需要被递归删除的关联表
     */
    String TAG_CASCADE_DELETE = "cascade-delete";

    String TAG_PARENT = "parent";


    String PROP_KIND_TO_ONE = "to-one";
    String PROP_KIND_TO_MANY = "to-many";

    String BIZ_OBJ_NAME_THIS_OBJ = GraphQLConstants.BIZ_OBJ_NAME_THIS_OBJ;

    String ACTION_ARG_THIS_OBJ = "thisObj";
    String ACTION_ARG_ENTITY = "entity";
    String ACTION_ARG_SVC_CONTEXT = CoreConstants.VAR_SVC_CTX;
    String ACTION_ARG_INVOCATION = "invocation";

    String GRAPHQL_EXTENSION_MAKER_CHECKER_TRY_RESPONSE = "nop.maker-checker.try-response";

    String SELECTION_COPY_FOR_NEW = "copyForNew";
}
