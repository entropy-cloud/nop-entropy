/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.util.IOrdered;

/**
 * 系统内置header均为小写字母
 */
public interface ApiConstants extends FilterBeanConstants {
    String[] EMPTY_STRING_ARRAY = new String[0];

    int API_STATUS_OK = 0;
    int API_STATUS_FAIL = -1;

    /**
     * 在ApiResponse中使用的预定义的status
     */
    int API_STATUS_BAD_REQUEST = 400;
    int API_STATUS_UNAUTHORIZED = 401;
    int API_STATUS_FORBIDDEN = 403;
    int API_STATUS_NOT_FOUND = 404;
    int API_STATUS_TIMEOUT = 408;

    String HTTP_HEADER_PREFIX = "http.";

    /**
     * API消息的版本，不同版本可以有不同的结构
     */
    String HEADER_VERSION = "nop-version";

    /**
     * 语言设置。请求和响应消息中的文本采用locale设置的语言
     */
    String HEADER_LOCALE = "nop-locale";

    /**
     * 请求和响应消息中的文本应该采用timezone设置的时区
     */
    String HEADER_TIMEZONE = "nop-timezone";

    /**
     * 租户id
     */
    String HEADER_TENANT = "nop-tenant";

    /**
     * 数据分片id
     */
    String HEADER_SHARD = "nop-shard";

    String HEADER_TRACE = "nop-trace";

    /**
     * 内部使用的header名前缀，一般情况下它们用于保存本地使用的临时变量，不会被发送到远端
     */
    String TEMP_HEADER_PREFIX = "__";

    /**
     * 身份认证token
     */
    String HEADER_ACCESS_TOKEN = "x-access-token";

    String HEADER_AUTHORIZATION = "authorization";

    String BEARER_TOKEN_PREFIX = "Bearer ";

    /**
     * 消息id
     */
    String HEADER_ID = "nop-id";

    /**
     * 控制nop-id一样时是否检查重复提交，以及发现重复提交时如何处理
     */
    String HEADER_IDEMPOTENT = "nop-idempotent";

    /**
     * 响应消息中携带的请求消息id, 用于消息匹配
     */
    String HEADER_REL_ID = "nop-rel-id";

    String HEADER_APP_ID = "nop-app-id";

    String HEADER_TAGS = "nop-tags";

    /**
     * 客户端的登录用户id
     */
    String HEADER_USER_ID = "nop-user-id";

    /**
     * 客户端的真实ip+端口，格式为ip:port
     */
    String HEADER_CLIENT_ADDR = "nop-client-addr";

    /**
     * 客户端设备id
     */
    String HEADER_DEVICE_ID = "nop-device-id";

    /**
     * 请求的超时时间
     */
    String HEADER_TIMEOUT = "nop-timeout";

    /**
     * actorName, 对应业务对象
     */
    String HEADER_ACTOR = "nop-actor";

    String HEADER_SVC_NAME = "nop-svc-name";

    String HEADER_TOPIC = "nop-topic";

    String HEADER_EVENT_TIME = "nop-event-time";

    String HEADER_PROCESS_TIME = "nop-process-time";

    /**
     * 业务对象上执行的方法
     */
    String HEADER_SVC_ACTION = "nop-svc-action";

    String HEADER_APP_ZONE = "nop-app-zone";

    String HEADER_ONE_WAY = "nop-one-way";

    /**
     * 服务路由，格式为 a:1.1;b:1.2这种形式，表示对服务a使用版本1.1，而对服务2使用版本1.2
     */
    String HEADER_SVC_ROUTE = "nop-svc-route";

    String HEADER_SVC_TAGS = "nop-svc-tags";

    /**
     * 服务端的域名和端口
     */
    String HEADER_HOST = "host";

    String HEADER_COOKIE = "cookie";

    String HEADER_CONTENT_TYPE = "content-type";

    /**
     * 任务id
     */
    String HEADER_TASK_ID = "nop-task-id";

    /**
     * 分布式事务组
     */
    String HEADER_TXN_GROUP = "nop-txn-group";

    /**
     * 分布式事务id
     */
    String HEADER_TXN_ID = "nop-txn-id";

    String HEADER_TXN_BRANCH_ID = "nop-txn-branch-id";

    /**
     * 分布式事务分支的顺序号，逻辑上存在依赖的分支之间，它们的顺序号按照从小到大的顺序排列。被依赖的分支，其顺序号较小。
     */
    String HEADER_TXN_BRANCH_NO = "nop-txn-branch-no";

    String HEADER_TCC_CANCEL = "nop-tcc-cancel";

    String HEADER_TCC_CONFIRM = "nop-tcc-confirm";

    /**
     * 业务唯一键，可以用于数据分区选择
     */
    String HEADER_BIZ_KEY = "nop-biz-key";

    String HEADER_SELECTION = "nop-selection";

    /**
     * 业务层面发现不可恢复的异常，分布式服务调用失败时如果发现返回这个header，则表示不应该发起重试。
     */
    //String HEADER_BIZ_FATAL = "nop-biz-fatal";

    /**
     * 服务调用返回success，但是业务层面服务调用没有成功，比如下发订单失败等，在tcc调用端会导致tcc事务回滚。
     */
    String HEADER_BIZ_FAIL = "nop-biz-fail";

    String PROP_TARGET_HOST = "targetHost";

    String SYS_PARAM_SELECTION = "@selection";

    /**
     * feign框架的QueryParam注解只接受编码后的参数名，
     */
    String ENCODED_PARAM_SELECTION = "%40selection";

    String META_KEY_TAGS = "tags";

    int BEAN_PROP_INCLUDE_JSON_IGNORE = 1;
    int BEAN_PROP_INCLUDE_WRITABLE = 2;
    int BEAN_PROP_INCLUDE_READABLE = 4;

    String TREE_BEAN_PROP_TYPE = "$type";
    String TREE_BEAN_PROP_BODY = "$body";
    String TREE_BEAN_PROP_LOC = "$loc";

    int INTERCEPTOR_PRIORITY_API_CONTEXT = IOrdered.NORMAL_PRIORITY - 2000;

    int INTERCEPTOR_PRIORITY_AUTH = INTERCEPTOR_PRIORITY_API_CONTEXT + 100;

    int INTERCEPTOR_PRIORITY_FLOW_CONTROL = INTERCEPTOR_PRIORITY_API_CONTEXT + 200;

    int INTERCEPTOR_PRIORITY_TCC = INTERCEPTOR_PRIORITY_API_CONTEXT + 300;

    int INTERCEPTOR_PRIORITY_CACHE = INTERCEPTOR_PRIORITY_API_CONTEXT + 400;

    int INTERCEPTOR_PRIORITY_SINGLE_SESSION = INTERCEPTOR_PRIORITY_API_CONTEXT + 500;

    int INTERCEPTOR_PRIORITY_TRANSACTIONAL = INTERCEPTOR_PRIORITY_API_CONTEXT + 600;

    String BEAN_SCOPE_SINGLETON = "singleton";

    String BEAN_SCOPE_PROTOTYPE = "prototype";

    String BEAN_SCOPE_REQUEST = "request";

    String DIRECTIVE_PROP_META = "PropMeta";
    String ARG_KEY_PROP = "keyProp";
    String ARG_ORDER_PROP = "orderProp";

    String PARAM_ENTITY_NAME = "entityName";
    String PARAM_FIELD_NAME = "fieldName";
    String PARAM_BIZ_OBJ_NAME = "bizObjName";
    String PARAM_OBJ_NAME = "objName";

    String PARAM_FIELD_DISPLAY_NAME = "fieldDisplayName";

    String PARAM_DISPLAY_NAME = "displayName";

    String PARAM_REF_ENTITY_NAME = "refEntityName";

    String PARAM_REF_BIZ_OBJ_NAME = "refBizObjName";

    String PARAM_PROP_NAME = "propName";

    String ATTR_SERVICE_CONTEXT = "serviceContext";


    String CONFIG_BEAN_ID = "@bean:id";
    String CONFIG_BEAN_CONTAINER = "@bean:container";
    String CONFIG_BEAN_TYPE = "@bean:type";

    String GRAPHQL_EXTENSION_ERROR_CODE = "nop-error-code";
    String GRAPHQL_EXTENSION_STATUS = "nop-status";
    String GRAPHQL_EXTENSION_MSG = "nop-msg";
    String GRAPHQL_EXTENSION_BIZ_FATAL = "nop-biz-fatal";

    /**
     * 用于表示不选择
     */
    String CONFIG_VALUE_NONE = "none";


    String MDC_NOP_TENANT = "nop-tenant";
    String MDC_NOP_SESSION = "nop-session";
    String MDC_NOP_USER = "nop-user";
    String MDC_NOP_TRACE = "nop-trace";

    String YES_VALUE = "1";
    String NO_VALUE = "0";

    String AUTH_FOR_READ = "read";

    String AUTH_FOR_WRITE = "write";

    String AUTH_FOR_DELETE = "delete";

    String AUTH_FOR_ALL = "all";

    String XML_PROP_TYPE = "$type";

    String DELTA_PATH_PREFIX = "/_delta/";
    String TENANT_PATH_PREFIX = "/_tenant/";

    String JOIN_TYPE_LEFT_JOIN = "leftJoin";
    String JOIN_TYPE_RIGHT_JOIN = "rightJoin";
    String JOIN_TYPE_INNER_JOIN = "innerJoin";
}