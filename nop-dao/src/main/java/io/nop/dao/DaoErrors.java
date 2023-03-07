/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface DaoErrors {
    String ARG_ENTITY_NAME = "entityName";
    String ARG_ENTITY_ID = "entityId";

    String ARG_QUERY_SPACE = "querySpace";
    String ARG_TXN_ID = "txnId";
    String ARG_TXN_GROUP = "txnGroup";
    String ARG_TXN = "txn";

    String ARG_COL_NAME = "colName";
    String ARG_COUNT = "count";

    String ARG_PROPS = "props";

    String ARG_PRODUCT_NAME = "productName";
    String ARG_PRODUCT_VERSION = "productVersion";

    String ARG_SQL = "sql";

    String ARG_NAME = "name";
    String ARG_ALLOWED_NAMES = "allowNames";
    String ARG_PARAM = "param";

    String ARG_SQL_NAME = "sqlName";
    String ARG_SQL_TEXT = "sqlText";
    String ARG_ACTION = "action";
    String ARG_SQL_STATE = "sqlState";
    String ARG_VENDOR_CODE = "vendorCode";

    String ARG_FUNC_NAME = "funcName";
    String ARG_MIN_ARG_COUNT = "minArgCount";
    String ARG_MAX_ARG_COUNT = "maxArgCount";
    String ARG_ARG_COUNT = "argCount";

    String ARG_DATA_TYPE = "dataType";
    String ARG_ALLOWED_TYPES = "allowedTypes";
    String ARG_PRECISION = "precision";

    String ARG_TS = "ts";
    String ARG_LAST_TS = "lastTs";

    ErrorCode ERR_DATASET_IS_READONLY = define("nop.err.dao.dataset.is-read-only", "只读数据集不允许修改");

    ErrorCode ERR_DATASET_UNKNOWN_COLUMN = define("nop.err.dao.dataset.unknown-column", "未知的数据列:{colName}",
            ARG_COL_NAME);

    ErrorCode ERR_TXN_NOT_IN_TRANSACTION = define("nop.err.dao.txn.not-in-transaction",
            "要求上下文环境必须存在已经启动的事务:{querySpace}", ARG_QUERY_SPACE);

    ErrorCode ERR_TXN_NOT_ALLOW_TRANSACTION = define("nop.err.dao.txn.not-allow-transaction", "不允许上下文环境中存在已经启动的事务",
            ARG_QUERY_SPACE);

    ErrorCode ERR_TXN_NOT_REGISTERED = define("nop.err.dao.txn.not-registered", "上下文环境中没有注册的事务对象:{txn}", ARG_TXN);

    ErrorCode ERR_TXN_COMMIT_FAIL = define("nop.err.dao.txn.commit-fail", "事务提交失败", ARG_TXN_GROUP);

    ErrorCode ERR_TXN_ROLLBACK_FAIL = define("nop.err.dao.txn.rollback-fail", "事务回滚失败", ARG_TXN_GROUP);

    ErrorCode ERR_TXN_ALREADY_STARTED = define("nop.err.dao.txn.already-started", "事务已经启动，不允许重复启动", ARG_TXN_GROUP);

    ErrorCode ERR_TXN_ALREADY_FINISHED = define("nop.err.dao.txn.already-finished", "事务已经结束，不允许此操作", ARG_TXN_GROUP);

    ErrorCode ERR_TXN_DUPLICATE_SUB_TRANSACTION = define("nop.err.dao.txn.duplicate-sub-transaction",
            "不允许注册多个txnGroup相同的子事务:{txnGroup}", ARG_TXN_GROUP);

    ErrorCode ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT = define("nop.err.dao.txn.rollback-only-not-allow-commit",
            "事务标记为只允许回滚，因此不能提交此事务:{txnGroup}", ARG_TXN_GROUP);

    ErrorCode ERR_DAO_UNKNOWN_ENTITY = define("nop.err.dao.unknown-entity", "类型为[{entityName}]，id为[{entityId}]的记录不存在",
            ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_DAO_MISSING_ENTITY_WITH_PROPS = define("nop.err.dao.missing-entity-with-props",
            "类型为[{entityName}]的记录不存在", ARG_ENTITY_NAME, ARG_PROPS);

    ErrorCode ERR_DAO_UNKNOWN_QUERY_SPACE = define("nop.err.dao.unknown-query-space", "未定义的数据源:{querySpace}",
            ARG_QUERY_SPACE);

    ErrorCode ERR_DAO_QUERY_SPACE_NOT_JDBC_CONNECTION = define("nop.err.dao.query-space-not-jdbc-connection",
            "querySpace[{querySpace}]对应的连接对象不是JDBC连接", ARG_QUERY_SPACE);

    ErrorCode ERR_DAO_NO_DIALECT_FOR_DATASOURCE = define("nop.err.dao.no-dialect-for-datasource",
            "没有适用于数据库[{productName}]的Dialect", ARG_PRODUCT_NAME, ARG_PRODUCT_VERSION);

    ErrorCode ERR_DIALECT_INVALID_TPL_PARAM = define("nop.err.dao.dialect.invalid-tpl-param",
            "非法的模板变量:{name},允许的变量为:{allowedNames}", ARG_NAME, ARG_ALLOWED_NAMES);

    ErrorCode ERR_DIALECT_TPL_PARAM_NO_ARG = define("nop.err.dao.dialect.tpl-param-no-arg", "模板变量的格式必须为name:arg");

    ErrorCode ERR_DIALECT_DATA_TYPE_NOT_SUPPORTED = define("nop.err.dao.dialect.data-type-not-supported",
            "不支持的数据类型:{dataType},允许的数据类型为:{allowedTypes}", ARG_DATA_TYPE, ARG_ALLOWED_TYPES);

    ErrorCode ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED = define("nop.err.dao.dialect.std-data-type-not-supported",
            "不支持的标准数据类型:{dataType},允许的数据类型为:{allowedTypes}", ARG_DATA_TYPE, ARG_ALLOWED_TYPES);

    ErrorCode ERR_DAO_FUNC_TOO_MANY_ARGS = define("nop.err.dao.func-too-many-args",
            "函数[{funcName}]的参数个数超过最大值{maxArgCount}", ARG_FUNC_NAME, ARG_MAX_ARG_COUNT);

    ErrorCode ERR_DAO_FUNC_TOO_FEW_ARGS = define("nop.err.dao.func-too-few-args",
            "函数[{funcName}]的参数个数少于最小值{minArgCount}", ARG_FUNC_NAME, ARG_MIN_ARG_COUNT);

    ErrorCode ERR_DAO_FUNC_INVALID_ARG_COUNT = define("nop.err.dao.func-invalid-arg-count",
            "函数[{funcName}]的参数个数必须为{argCount}", ARG_FUNC_NAME, ARG_ARG_COUNT);

    ErrorCode ERR_SQL_BAD_SQL_GRAMMAR = define("nop.err.dao.sql.bad-sql-grammar", "SQL语法错误或者数据库对象不存在", ARG_SQL_TEXT);

    ErrorCode ERR_SQL_DUPLICATE_KEY = define("nop.err.dao.sql.duplicate-key", "数据库记录的键值冲突", ARG_SQL_TEXT);

    ErrorCode ERR_SQL_DATA_INTEGRITY_VIOLATION = define("nop.err.dao.sql.data-integrity-violation", "违反数据库完整性约束",
            ARG_SQL_TEXT);

    ErrorCode ERR_SQL_DATA_TYPE_CONVERSION_FAIL = define("nop.err.dao.sql.data-type-conversion-fail", "数据类型转换错误");

    ErrorCode ERR_SQL_DATA_EXCEPTION = define("nop.err.dao.sql.data-exception", "数据异常", ARG_SQL_TEXT);

    ErrorCode ERR_SQL_DATA_ACCESS = define("nop.err.dao.sql.data-access", "数据库访问失败", ARG_SQL_TEXT);

    ErrorCode ERR_SQL_DEAD_LOCK = define("nop.err.dao.sql.dead-lock", "数据库死锁", ARG_SQL_TEXT);

    ErrorCode ERR_SQL_INVALID_AUTHORIZATION = define("nop.err.dao.sql.invalid-authorization", "数据库权限验证失败",
            ARG_SQL_TEXT);

    ErrorCode ERR_SQL_TRANSIENT_CONNECTION_FAIL = define("nop.err.dao.sql.transient-connection-fail", "数据库连接暂时失败，可重试",
            ARG_SQL_TEXT);

    ErrorCode ERR_SQL_CONCURRENCY_FAILURE = define("nop.err.dao.sql.concurrency-failure", "数据库并发冲突导致回滚", ARG_SQL_TEXT);

    ErrorCode ERR_SQL_TIMEOUT = define("nop.err.dao.sql.timeout", "数据库操作超时", ARG_SQL_TEXT);

    ErrorCode ERR_SQL_FEATURE_NOT_SUPPORTED = define("nop.err.dao.sql.feature-not-supported", "不支持的数据库特性",
            ARG_SQL_TEXT);

    ErrorCode ERR_SQL_RECOVERABLE_DATA_ACCESS = define("nop.err.dao.sql.recoverable-data-access", "数据访问失败，可获取新的连接再重试",
            ARG_SQL_TEXT);

    ErrorCode ERR_SQL_UNCATEGORIZED_FAILURE = define("nop.err.dao.sql.uncategorized-failure", "数据库异常", ARG_SQL_TEXT);

    ErrorCode ERR_DAO_NO_DATA_SOURCE_AVAILABLE = define("nop.err.dao.no-data-source-available", "没有可用的数据库连接池");

    ErrorCode ERR_SQL_INVALID_RESULT_SET_ACCESS = define("nop.err.dao.sql.invalid-resultset-access", "访问数据集失败");

    ErrorCode ERR_SQL_CANNOT_SERIALIZE_TRANSACTION = define("nop.err.dao.sql.cannot-serialize-transaction",
            "数据库事务序列化失败");

    ErrorCode ERR_DAO_INVALID_TIMESTAMP =
            define("nop.err.dao.invalid-snowflake-timestamp",
                    "非法的时间戳，系统时钟不允许回拨；ts={},lastTs={}", ARG_TS, ARG_LAST_TS);
}