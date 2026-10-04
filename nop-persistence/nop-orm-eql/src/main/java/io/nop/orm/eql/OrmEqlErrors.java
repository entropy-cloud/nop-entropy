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
    String ARG_WINDOW_NAME = "windowName";
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

    String ARG_ROW_INDEX = "rowIndex";


    String ARG_DECORATOR = "decorator";
    String ARG_EXPECTED_COUNT = "expectedCount";
    String ARG_ARG_INDEX = "argIndex";

    String ARG_SQL = "sql";

    String ARG_FEATURE = "feature";

    String ARG_EXPR = "expr";

    String ARG_OPERATOR1 = "operator1";
    String ARG_OPERATOR2 = "operator2";
    String ARG_COLLECTION_PREFIX1 = "collectionPrefix1";
    String ARG_COLLECTION_PREFIX2 = "collectionPrefix2";
    String ARG_COLLECTION1 = "collection1";
    String ARG_COLLECTION2 = "collection2";
    String ARG_REASON = "reason";

    // WI10b: IN 列表长度门禁（EQL 编译路径）；dialect 复用上方 ARG_DIALECT
    String ARG_ITEM_COUNT = "itemCount";
    String ARG_MAX_ITEMS = "maxItems";
    String ARG_STMT_NAME = "stmtName";

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

    ErrorCode ERR_EQL_VALUES_ROW_ARITY_MISMATCH = define("nop.err.eql.values-row-arity-mismatch",
            "VALUES 表源第{rowIndex}行的元素数量与第1行不一致", ARG_ROW_INDEX, ARG_ARG_COUNT);
    ErrorCode ERR_EQL_PARAM_NOT_COMPONENT = define("nop.err.eql.param-not-component",
            "参数[{paramIndex}]不是IOrmComponent类型[{expected}]");

    ErrorCode ERR_EQL_PARAM_NOT_EXPECTED_ENTITY = define("nop.err.eql.param-not-expected-entity",
            "参数[{paramIndex}]不是期待的实体类型[{expected}]");

    ErrorCode ERR_EQL_DECORATOR_ARG_COUNT_IS_NOT_EXPECTED = define("nop.err.eql.decorator-arg-count-is-not-expected",
            "注解[{decorator}]的参数必须为{expectedCount}", ARG_DECORATOR, ARG_EXPECTED_COUNT);

    ErrorCode ERR_EQL_DECORATOR_ARG_TYPE_IS_NOT_EXPECTED = define("nop.err.eql.decorator-arg-type-is-not-expected",
            "注解[{decorator}]的参数[{argIndex}]的类型不是期待的数据类型:{expected}", ARG_DECORATOR, ARG_ARG_INDEX, ARG_EXPECTED);

    ErrorCode ERR_EQL_FUNC_TOO_FEW_ARGS = define("nop.err.eql.func-too-few-args", "函数[{funcName}]的参数个数不足",
            ARG_FUNC_NAME);

    ErrorCode ERR_EQL_FUNC_TOO_MANY_ARGS = define("nop.err.eql.func-too-many-args", "函数[{funcName}]的参数个数过多",
            ARG_FUNC_NAME);

    ErrorCode ERR_EQL_UNKNOWN_FUNCTION = define("nop.err.eql.unknown-function", "未知的函数[{funcName}]", ARG_FUNC_NAME);

    ErrorCode ERR_EQL_UNKNOWN_WINDOW_NAME = define("nop.err.eql.unknown-window-name",
            "未声明的窗口名[{windowName}]", ARG_WINDOW_NAME);

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

    // WI1 BUG-14（裁定=候选B 显式报错）：SqlCollectionAccessExpr 的 [..] 是集合过滤语义，
    // 编译管线无 lowering，机械回显没有正确输出目标 ⇒ 生成器显式失败而非静默产出垃圾 SQL
    ErrorCode ERR_EQL_COLLECTION_ACCESS_NOT_SUPPORT = define("nop.err.eql.collection-access-not-support",
            "集合下标表达式[{expr}]不支持翻译为SQL，请改写为关联属性join或集合操作符(_some/_all)", ARG_EXPR);

    // WI2 BUG-15（裁定=候选A 解析期拒绝，裁定文档 §六）：SqlTypeExpr 的 characterSet/collate
    // 零消费点（AstToEqlGenerator 只打印 name/precision/scale，AstToSqlGenerator.visitSqlCastExpr
    // 绕过 SqlTypeExpr），字段有值即静默丢弃。手写访问器被调用 = 分支可达 = 显式拒绝，
    // 防止未来文法修复后退化为 BUG-1 同型静默丢弃
    ErrorCode ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT = define("nop.err.eql.sql-type-clause-not-support",
            "CAST类型表达式的[{value}]子句不支持翻译为SQL（无任何方言消费其语义），显式拒绝", ARG_VALUE);

    // WI8（plan 27；裁定文档 §四.3 + Minimum Rules #24）：IS [NOT] TRUE/FALSE 的构建期展开与
    // IS [NOT] DISTINCT FROM 的方言展开（transformer guard 式）会把操作数复制为多份。操作数含
    // 参数标记（?）时复制改变调用方参数契约（1 个 EQL 参数 → N 个 JDBC 参数，绑定错位），
    // 必须响亮拒绝而非静默复制；不含标记的操作数（列/实体属性/字面量表达式）不受影响
    ErrorCode ERR_EQL_NULL_PREDICATE_OPERAND_HAS_PARAM_MARKER = define(
            "nop.err.eql.null-predicate-operand-has-param-marker",
            "NULL语义谓词[{expr}]的操作数包含参数标记(?)——lowering展开会复制操作数导致参数绑定错位，请改用列或实体属性作操作数",
            ARG_EXPR);

    ErrorCode ERR_EQL_UNSUPPORTED_EVAL_EXPR =
            define("nop.err.eql.unsupported-eval-expr", "不支持的表达式类型", ARG_EXPR);

    ErrorCode ERR_EQL_INVALID_BIT_LITERAL = define("nop.err.eql.invalid-bit-literal",
            "非法的二进制字面量:{value}", ARG_VALUE);

    ErrorCode ERR_EQL_INVALID_HEX_LITERAL = define("nop.err.eql.invalid-hex-literal",
            "非法的十六进制字面量:{value}", ARG_VALUE);

    ErrorCode ERR_EQL_INVALID_COLLECTION_OPERATOR_PATH = define("nop.err.eql.invalid-collection-operator-path",
            "集合操作符路径不合法:{propPath}", ARG_PROP_PATH);

    ErrorCode ERR_EQL_NOT_SINGLE_EXPR =
            define("nop.err.eql.not-single-expr", "不是合法SQL表达式", ARG_SQL);

    ErrorCode ERR_EQL_COLLECTION_PREFIX_MISMATCH = define("nop.err.eql.collection-prefix-mismatch",
            "集合操作符的collectionPrefix不匹配: scope1=[{collectionPrefix1}], scope2=[{collectionPrefix2}]",
            ARG_COLLECTION_PREFIX1, ARG_COLLECTION_PREFIX2);

    ErrorCode ERR_EQL_MULTIPLE_COLLECTION_IN_SAME_BRANCH = define("nop.err.eql.multiple-collection-in-same-branch",
            "同一个逻辑分支不支持多个集合表达式: collection1=[{collection1}], collection2=[{collection2}]",
            ARG_COLLECTION1, ARG_COLLECTION2);

    ErrorCode ERR_EQL_CANNOT_MERGE_COLLECTION_SCOPES = define("nop.err.eql.cannot-merge-collection-scopes",
            "无法合并集合操作符作用域: operator1={operator1}, operator2={operator2}",
            ARG_OPERATOR1, ARG_OPERATOR2);

    ErrorCode ERR_EQL_INVALID_WINDOW_FRAME = define("nop.err.eql.invalid-window-frame",
            "非法的窗口frame定义:{value}", ARG_VALUE);

    // WI17: TUMBLE(t, INTERVAL) 的 INTERVAL 字面量必须是正的固定时长（日历单位
    // MONTH/QUARTER/YEAR 无固定毫秒时长，进 fail-fast）
    ErrorCode ERR_EQL_INVALID_INTERVAL_VALUE = define("nop.err.eql.invalid-interval-value",
            "非法的INTERVAL时长定义:{value}", ARG_VALUE);

    // WI10b（WI10a 设计定稿 2026-10-03）：EQL 编译路径的 IN 列表长度门禁。统一阈值 1000
    // （唯一硬依据 Oracle ORA-01795），经 CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE 可调高、
    // 无关闭取值；门禁方言无关、超限显式报错、不做改写；NOT IN 与 IN 同为 SqlInValuesExpr
    // 节点同受本门。回显路径（EqlASTNode#toSqlString）不设门，保持忠实转储。
    // 与 nop-core 侧 nop.err.core.sql.in-list-too-large 契约一致但不共用错误码
    ErrorCode ERR_EQL_IN_LIST_TOO_LARGE = define("nop.err.eql.in-list-too-large",
            "IN列表的元素个数[{itemCount}]超过上限[{maxItems}],语句[{stmtName}],方言[{dialect}],请分批查询或调高配置nop.core.sql.in-list-max-size",
            ARG_ITEM_COUNT, ARG_MAX_ITEMS, ARG_STMT_NAME, ARG_DIALECT);
}
