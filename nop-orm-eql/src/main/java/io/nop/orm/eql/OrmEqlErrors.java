/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;
import static io.nop.orm.model.OrmModelErrors.ARG_DATA_TYPE;

public interface OrmEqlErrors {

    String ARG_ENTITY_NAME = "entityName";
    String ARG_ENTITY_ID = "entityId";
    String ARG_PROP_NAME = "propName";

    String ARG_EXPECTED = "expected";
    String ARG_PARAM_INDEX = "paramIndex";

    String ARG_OP = "op";
    String ARG_VALUE = "value";
    String ARG_ALIAS = "alias";
    String ARG_TABLE1 = "table1";
    String ARG_TABLE2 = "table2";

    String ARG_LEFT_SOURCE = "leftSource";
    String ARG_RIGHT_SOURCE = "rightSource";
    String ARG_PROP_PATH = "propPath";
    String ARG_QUERY_SPACE = "querySpace";
    String ARG_COL_NAME = "colName";
    String ARG_FUNC_NAME = "funcName";
    String ARG_ARG_COUNT = "argCount";
    String ARG_MIN_ARG_COUNT = "minArgCount";
    String ARG_MAX_ARG_COUNT = "maxArgCount";
    String ARG_DIALECT = "dialect";
    String ARG_FIELD_NAME = "fieldName";

    String ARG_QUERY_SPACE_MAP = "querySpaceMap";
    String ARG_TABLE = "table";

    String ARG_AST_NODE = "astNode";

    String ARG_DOMAIN_DATA_TYPE = "domainDataType";

    String ARG_ALLOWED_NAMES = "allowedNames";


    String ARG_PATH = "path";

    String ARG_SQL_TYPE = "sqlType";


    String ARG_TABLE_SOURCE = "tableSource";


    String ARG_DECORATOR = "decorator";
    String ARG_EXPECTED_COUNT = "expectedCount";
    String ARG_ARG_INDEX = "argIndex";

    String ARG_SQL = "sql";

    String ARG_FEATURE = "feature";

    ErrorCode ERR_ORM_NULL_BINDER_FOR_COLUMN = define("nop.err.orm.null-binder-for-column",
            "对象[{entityName}]的列[{colName}]的类型为[{sqlType}],没有找到对应的数据绑定接口",
            ARG_ENTITY_NAME, ARG_COL_NAME, ARG_SQL_TYPE, ARG_DATA_TYPE);

    ErrorCode ERR_EQL_UNSUPPORTED_OP = define("nop.err.eql.unsupported-op", "不支持的运算符:{op}", ARG_OP);

    ErrorCode ERR_EQL_PRECISION_NOT_POSITIVE_INT = define("nop.err.eql.precision-not-positive-int",
            "数据精度的设置值[{value}]不是正整数", ARG_VALUE);

    ErrorCode ERR_EQL_SCALE_NOT_NON_NEGATIVE_INT = define("nop.err.eql.scale-not-non-negative-int",
            "数据小数位数的值[{value}]不是非负整数", ARG_VALUE);

    ErrorCode ERR_EQL_INVALID_DATETIME_TYPE = define("nop.err.eql.invalid-datetime-type", "时间日期类型只能是t,s,或者ts",
            ARG_VALUE);

    ErrorCode ERR_EQL_INVALID_INTERVAL_UNIT = define("nop.err.eql.invalid-interval-unit",
            "时间区间类型只能是MICROSECOND/SECOND/MINUTE/HOUR/DAY/WEEK/MONTH/QUARTER/YEAR", ARG_VALUE);

    ErrorCode ERR_EQL_DUPLICATE_TABLE_ALIAS = define("nop.err.eql.duplicate-table-alias",
            "数据源[{table1}]和[{table2}]的别名[{alias}]相同", ARG_TABLE1, ARG_TABLE2, ARG_ALIAS);

    ErrorCode ERR_EQL_JOIN_NO_CONDITION = define("nop.err.eql.join-no-condition", "表关联没有指定连接条件");

    ErrorCode ERR_EQL_UNKNOWN_ENTITY_NAME = define("nop.err.eql.unknown-entity-name", "未知的实体名[{entityName}]",
            ARG_ENTITY_NAME);

    ErrorCode ERR_EQL_UNKNOWN_ALIAS = define("nop.err.eql.unknown-alias", "未知的实体别名[{alias}]", ARG_ALIAS, ARG_PROP_PATH);

    ErrorCode ERR_EQL_OWNER_NOT_REF_TO_ENTITY = define("nop.err.eql.owner-not-ref-to-entity", "属性表达式的owner必须是实体对象");

    ErrorCode ERR_EQL_JOIN_RIGHT_SOURCE_MUST_BE_PROP_PATH_OF_LEFT_SOURCE = define(
            "nop.err.eql.join-right-source-must-be-prop-path-of-left-source",
            "关联关系的右侧如果不是实体对象，则必须是左侧对象的关联属性，例如UserInfo u left join u.dept", ARG_LEFT_SOURCE, ARG_RIGHT_SOURCE);

    ErrorCode ERR_EQL_PROP_PATH_NOT_VALID_TO_ONE_REFERENCE = define("nop.err.eql.prop-path-not-valid-to-one-reference",
            "关联属性表达式[{propName}]不是[{entityName}]对象的多对一关联属性", ARG_PROP_NAME, ARG_ENTITY_NAME);

    ErrorCode ERR_EQL_INVALID_SQL_TYPE = define("nop.err.eql.invalid-sql-type",
            "未定义的SQL数据类型[{sqlType}]。只允许StdSqlType中定义的常量:{allowedNames}", ARG_SQL_TYPE, ARG_ALLOWED_NAMES);

    ErrorCode ERR_EQL_SELECT_NO_PROJECTIONS = define("nop.err.eql.select-no-projections", "select语句没有指定选择字段列表");

    ErrorCode ERR_EQL_RETURNING_NO_PROJECTIONS = define("nop.err.eql.returning-no-projections", "returning语句没有指定选择字段列表");

    ErrorCode ERR_EQL_FIELD_NOT_IN_SUBQUERY = define("nop.err.eql.field-not-in-subquery",
            "查询语句的返回结果中没有包含字段:{fieldName}", ARG_FIELD_NAME);

    ErrorCode ERR_EQL_PROP_PATH_JOIN_NOT_ALLOW_CONDITION = define("nop.err.eql.prop-path-join-not-allow-condition",
            "关联属性表达式[{propPath}]时不允许指定关联条件，具体关联条件应根据实体模型的属性定义来自动推定", ARG_PROP_PATH);

    ErrorCode ERR_EQL_JOIN_PROP_PATH_IS_DUPLICATED = define("nop.err.eql.join-prop-path-is-duplicated",
            "同一关联属性表达式[{propPath}]不能在join语句中多次定义", ARG_PROP_PATH);

    ErrorCode ERR_EQL_TABLE_SOURCE_MUST_HAS_ALIAS = define("nop.err.eql.table-source-must-has-alias", "数据源必须具有别名");

    ErrorCode ERR_EQL_QUERY_NO_FROM_CLAUSE = define("nop.err.eql.query-no-from-clause", "sql语句缺乏from子句");

    ErrorCode ERR_EQL_ONLY_SUPPORT_SINGLE_TABLE_SOURCE = define("nop.err.eql.only-support-single-table-source",
            "只支持实体表数据源", ARG_TABLE_SOURCE);

    ErrorCode ERR_EQL_NOT_ALLOW_MULTIPLE_QUERY_SPACE = define("nop.err.eql.not-allow-multiple-query-space",
            "一条sql语句中的所有表对应的querySpace都必须相同");

    ErrorCode ERR_EQL_NOT_SUPPORT_MULTIPLE_STATEMENT = define("nop.err.eql.not-support-multiple-statement",
            "一次调用只允许执行一条SQL语句");

    ErrorCode ERR_EQL_UNKNOWN_QUERY_SPACE = define("nop.err.eql.unknown-query-space",
            "sql语句使用了未知的querySpace[{querySpace}]", ARG_QUERY_SPACE);

    ErrorCode ERR_EQL_UNKNOWN_COLUMN_NAME = define("nop.err.eql.unknown-column-name", "sql语句使用了未知的列名[{colName}]",
            ARG_COL_NAME);

    ErrorCode ERR_EQL_TABLE_SOURCE_NOT_RESOLVED = define("nop.err.eql.table-source-not-resolved",
            "tableSource没有被成功解析，无法确定它的具体来源");

    ErrorCode ERR_EQL_PARAM_NOT_COMPONENT = define("nop.err.eql.param-not-component",
            "参数[{paramIndex}]不是IOrmComponent类型[{expected}]");

    ErrorCode ERR_EQL_PARAM_NOT_EXPECTED_ENTITY = define("nop.err.eql.param-not-expected-entity",
            "参数[{paramIndex}]不是期待的实体类型[{expected}]");

    ErrorCode ERR_EQL_DECORATOR_ARG_COUNT_IS_NOT_EXPECTED = define("nop.err.eql.decorator-arg-count-is-not-expected",
            "注解[{decorator}]的参数必须为{expectedCount}", ARG_DECORATOR, ARG_EXPECTED_COUNT);

    ErrorCode ERR_EQL_DECORATOR_ARG_TYPE_IS_NOT_EXPECTED = define("nop.err.eql.decorator-arg-type-is-not-expected",
            "注解[{decorator}]的参数[{argIndex}]的类型不是期待的数据类型:{expected}", ARG_DECORATOR, ARG_ARG_INDEX, ARG_EXPECTED);

    ErrorCode ERR_EQL_FUNC_TOO_FEW_ARGS = define("nop.err.eql.func-too-few-args", "函数[{funcName]的参数个数不足",
            ARG_FUNC_NAME);

    ErrorCode ERR_EQL_FUNC_TOO_MANY_ARGS = define("nop.err.eql.func-too-many-args", "函数[{funcName]的参数个数过多",
            ARG_FUNC_NAME);

    ErrorCode ERR_EQL_UNKNOWN_FUNCTION = define("nop.err.eql.unknown-function", "未知的函数[{funcName]", ARG_FUNC_NAME);

    ErrorCode ERR_EQL_FUNC_ONLY_ALLOW_IN_WINDOW_EXPR =
            define("nop.err.eql.func-only-allow-in-window-expr", "函数[{funcName}]只允许在窗口表达式中使用", ARG_FUNC_NAME);

    ErrorCode ERR_EQL_NOT_SUPPORT_ILIKE = define("nop.err.eql.not-support-ilike-operator", "数据库不支持ilike运算符", ARG_FUNC_NAME);

    ErrorCode ERR_EQL_UNKNOWN_FIELD_IN_SELECTION =
            define("nop.err.eql.unknown-field-in-selection", "字段选择列表中没有属性:{propName}", ARG_PROP_NAME);

    ErrorCode ERR_EQL_UNKNOWN_FIELD_IN_ENTITY =
            define("nop.err.eql.unknown-field-in-entity", "实体[{entityName}]上没有属性:{propName}",
                    ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_EQL_NOT_SUPPORT_MULTI_JOIN_ON_ALIAS =
            define("nop.err.eql.not-support-multi-join-on-alias", "表关联不支持多个别名字段关联条件");

    ErrorCode ERR_EQL_FIELD_NOT_PROP =
            define("nop.err.eql.field-not-prop", "字段不是实体上的属性:{fieldName}", ARG_FIELD_NAME);

    ErrorCode ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE =
            define("nop.err.eql.dialect-not-support-feature", "数据库方言[{dialect}]不支持特性[{feature}]",
                    ARG_DIALECT, ARG_FEATURE);
}
