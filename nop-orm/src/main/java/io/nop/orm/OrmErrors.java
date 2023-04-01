/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface OrmErrors {
    String ARG_ENTITY = "entity";
    String ARG_ENTITY_NAME = "entityName";
    String ARG_ENTITY_ID = "entityId";
    String ARG_PROP_NAME = "propName";

    String ARG_OTHER_ENTITY_NAME = "otherEntityName";

    String ARG_ENTITY_PROP_NAME = "entityPropName";

    String ARG_TABLE_NAME = "tableName";

    String ARG_OTHER_LOC = "otherLoc";

    String ARG_OTHER_PROP_NAME = "otherPropName";

    String ARG_PROP_NAMES = "propNames";

    String ARG_METHOD = "method";

    String ARG_LOOP_ENTITY_NAMES = "loopEntityNames";

    String ARG_REF_NAME = "refName";
    String ARG_REF_ENTITY_NAME = "refEntityName";

    String ARG_TENANT_ID = "tenantId";
    String ARG_NEW_TENANT_ID = "newTenantId";
    String ARG_CURRENT_TENANT = "currentTenant";

    String ARG_REV_BEGIN_VER = "revBeginVer";
    String ARG_REV_END_VER = "revEndVer";
    String ARG_REV_VER = "revVer";

    String ARG_PROP_ID = "propId";
    String ARG_PROP_ID_BOUND = "propIdBound";

    String ARG_VERSION = "version";
    String ARG_OLD_VERSION = "oldVersion";

    String ARG_OWNER = "owner";
    String ARG_OWNER_PROP = "ownerProp";
    String ARG_COLLECTION_NAME = "collectionName";

    String ARG_COUNT = "count";
    String ARG_EXPECTED = "expected";
    String ARG_SQL = "sql";
    String ARG_PARAM_POS = "paramPos";

    String ARG_PART = "part";
    String ARG_SRC_TYPE = ApiErrors.ARG_SRC_TYPE;
    String ARG_TARGET_TYPE = ApiErrors.ARG_TARGET_TYPE;

    String ARG_DAO_ENTITY_NAME = "daoEntityName";

    String ARG_OP = "op";
    String ARG_VALUE = "value";
    String ARG_ALIAS = "alias";
    String ARG_TABLE1 = "table1";
    String ARG_TABLE2 = "table2";
    String ARG_TABLE = "table";

    String ARG_LEFT_SOURCE = "leftSource";
    String ARG_RIGHT_SOURCE = "rightSource";
    String ARG_PROP_PATH = "propPath";
    String ARG_QUERY_SPACE_MAP = "querySpaceMap";
    String ARG_QUERY_SPACE = "querySpace";
    String ARG_COL_NAME = "colName";
    String ARG_FUNC_NAME = "funcName";
    String ARG_ARG_COUNT = "argCount";
    String ARG_MIN_ARG_COUNT = "minArgCount";
    String ARG_MAX_ARG_COUNT = "maxArgCount";
    String ARG_DIALECT = "dialect";
    String ARG_FIELD_NAME = "fieldName";

    String ARG_AST_NODE = "astNode";

    String ARG_DOMAIN_DATA_TYPE = "domainDataType";

    String ARG_ALLOWED_NAMES = "allowedNames";
    String ARG_DOMAIN = "domain";

    String ARG_COL_CODE = "colCode";

    String ARG_PATH = "path";
    String ARG_SQL_NAME = "sqlName";
    String ARG_SQL_ITEM_NAME = "sqlItemName";
    String ARG_INDEX = "index";

    String ARG_STATUS = "status";

    String ARG_SCOPE = "scope";
    String ARG_BEAN_NAME = "beanName";

    String ARG_DATA_TYPE = "dataType";
    String ARG_SQL_TYPE = "sqlType";

    String ARG_COMPONENT_CLASS = "componentClass";

    String ARG_TABLE_SOURCE = "tableSource";

    String ARG_PARAM_INDEX = "paramIndex";

    String ARG_DECORATOR = "decorator";
    String ARG_EXPECTED_COUNT = "expectedCount";
    String ARG_ARG_INDEX = "argIndex";

    String ARG_SOURCE_NAME = "sourceName";
    String ARG_SUB_SOURCE_NAME = "subSourceName";

    String ARG_NAME = "name";

    String ARG_PROP_CLASS = "propClass";

    ErrorCode ERR_ORM_SESSION_CLOSED = define("nop.err.orm.session-closed", "session已关闭");

    ErrorCode ERR_ORM_READONLY_NOT_ALLOW_UPDATE = define("nop.err.orm.read-only-session-not-allow-update",
            "只读的session不支持修改操作");

    ErrorCode ERR_ORM_UNKNOWN_ENTITY_PERSISTER = define("nop.err.orm.unknown-entity-persister",
            "未定义的实体持久化类:{entityName}", ARG_ENTITY_NAME);

    ErrorCode ERR_ORM_UNKNOWN_COLLECTION_PERSISTER = define("nop.err.orm.unknown-collection-persister",
            "未定义的集合持久化类:{collectionName}", ARG_COLLECTION_NAME);

    ErrorCode ERR_ORM_ENTITY_NOT_ATTACHED = define("nop.err.orm.session.not-attached", "实体未与session关联，不能访问");

    ErrorCode ERR_ORM_NULL_ENTITY_MODEL = define("nop.err.orm.entity.null-entity-model", "实体的元数据对象没有初始化", ARG_ENTITY);

    ErrorCode ERR_ORM_ALREADY_HAS_ENTITY_MODEL = define("nop.err.orm.entity.already-has-entity-model",
            "实体的元数据对象已经初始化，不允许再次设置", ARG_ENTITY);

    ErrorCode ERR_ORM_PROP_CHANGE_NOT_ALLOW_DIRTY = define("nop.err.orm.entity.prop-change-not-allow-dirty",
            "已经被修改的字段不允许执行差量更新", ARG_ENTITY, ARG_PROP_NAME);

    ErrorCode ERR_ORM_ENTITY_PK_NO_PROP = define("nop.err.orm.entity-pk-nop-prop", "实体的复合主键不包含属性[{propName}]",
            ARG_PROP_NAME, ARG_PROP_NAMES);

    ErrorCode ERR_ORM_ENTITY_PROP_ID_NOT_MATCH_DEF_IN_MODEL = define(
            "nop.err.orm.entity-prop-id-not-match-def-in-model",
            "实体[{entityName}]中的属性编号顺序与模型中的定义顺序不一致：编号为[{propId}]的属性在实体中对应属性[{entityPropName}],"
                    + "在模型中对应属性[{propName}]。可能需要重新生成代码。",
            ARG_ENTITY_NAME, ARG_PROP_ID, ARG_PROP_NAME, ARG_ENTITY_PROP_NAME);

    ErrorCode ERR_ORM_TENANT_ID_NOT_ALLOW_CHANGE = define("nop.err.orm.tenant-id-not-allow-change", "租户ID不允许被改变",
            ARG_ENTITY_NAME, ARG_ENTITY_ID, ARG_TENANT_ID);

    ErrorCode ERR_ORM_UNKNOWN_ENTITY_NAME = define("nop.err.orm.entity.unknown-entity-name", "未定义的对象名:{entityName}",
            ARG_ENTITY_NAME);

    ErrorCode ERR_ORM_UNKNOWN_ENTITY_MODEL_FOR_TABLE = define("nop.err.orm.entity.unknown-entity-model-for-table",
            "没有定义实体模型对应于数据库表:{tableName}", ARG_TABLE_NAME);

    ErrorCode ERR_ORM_UNKNOWN_COLLECTION_NAME = define("nop.err.orm.entity.unknown-collection-name",
            "未定义的对象集合:{collectionName}", ARG_COLLECTION_NAME);

    ErrorCode ERR_ORM_DAO_ENTITY_NAME_NOT_FOR_DAO = define("nop.err.orm.dao.entity-name-not-for-dao",
            "[{entityName}]不是OrmEntityDao所管理的对象类[{daoEntityName}]", ARG_ENTITY_NAME, ARG_DAO_ENTITY_NAME);

    ErrorCode ERR_ORM_UPDATE_ENTITY_NO_CURRENT_SESSION = define("nop.err.orm.dao.update-entity-no-current-session",
            "更新实体时上下文环境必须存在已经打开的OrmSession", ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_UPDATE_ENTITY_NOT_MANAGED = define("nop.err.orm.dao.update-entity-not-managed",
            "只有从数据库中成功加载的对象才能调用更新操作", ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_NOT_ALLOW_LOCK_DIRTY_ENTITY = define("nop.err.orm.lock-entity-must-not-be-dirty",
            "必须先锁定对象然后才能修改，不允许锁定已经被修改的对象", ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_LOCK_MUST_RUN_IN_TXN = define("nop.err.orm.lock-must-run-in-txn", "lock语句必须在事务中执行",
            ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_LOCK_ENTITY_FAIL = define("nop.err.orm.lock-entity-fail", "锁定对象[{entityName}:{entityId}]失败",
            ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_NOT_SUPPORT_COMPUTE = define("nop.err.orm.not-support-compute",
            "对象[{entityName}:{entityId}]不支持计算属性[{propName}]", ARG_ENTITY_NAME, ARG_ENTITY_ID, ARG_PROP_NAME);

    ErrorCode ERR_ORM_ENTITY_PROP_NOT_REF_COLLECTION = define("nop.err.orm.entity-prop-not-ref-collection",
            "对象[{entityName}]的属性[{propName}]不是集合属性", ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_ENTITY_PROP_NOT_REF_ENTITY = define("nop.err.orm.entity-prop-not-ref-entity",
            "对象[{entityName}]的属性[{propName}]不是对象引用", ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_ENTITY_PROP_NOT_ALLOW_SET = define("nop.err.orm.entity-prop-not-allow-set",
            "实体对象[{entityName}:{entityId}]的属性[{propName}]不支持set方法", ARG_ENTITY_NAME, ARG_ENTITY_ID, ARG_PROP_NAME);

    ErrorCode ERR_ORM_ENTITY_PROP_NOT_UPDATABLE = define("nop.err.orm.entity-prop-not-updatable",
            "实体对象[{entityName}]的属性[{propName}]不支持修改", ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_UPDATE_ENTITY_NOT_FOUND = define("nop.err.orm.update-entity-not-found",
            "更新实体[{entityName}:{entityId}]失败，未找到匹配记录，可能是乐观锁更新失败", ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_UPDATE_ENTITY_MULTIPLE_ROWS = define("nop.err.orm.update-entity-multiple-rows",
            "更新实体[{entityName}:{entityId}]时发现多条重复记录");

    ErrorCode ERR_ORM_ENTITY_UNKNOWN_PROP = define("nop.err.orm.entity-unknown-prop",
            "实体对象[{entityName}:{entityId}]不支持属性[{propName}]", ARG_ENTITY_NAME, ARG_ENTITY_ID, ARG_PROP_NAME);

    ErrorCode ERR_ORM_INVALID_PROP_ID = define("nop.err.orm.invalid-prop-id",
            "实体对象[{entityName}:{entityId}]的propId必须在1和{propIdBound}之间:{propId}", ARG_ENTITY_NAME, ARG_ENTITY_ID,
            ARG_PROP_ID, ARG_PROP_ID_BOUND);

    ErrorCode ERR_ORM_MISSING_TENANT_ID = define("nop.err.orm.missing-tenant-id",
            "实体对象[{entityName}:{entityId}]没有设置租户属性[{propName}]", ARG_ENTITY_NAME, ARG_ENTITY_ID, ARG_PROP_NAME);

    ErrorCode ERR_ORM_ENTITY_ID_NOT_SET = define("nop.err.orm.entity-id-not-set", "实体的主键字段[{propName}]没有被设置",
            ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_NOT_ALLOW_PROCESS_ENTITY_IN_OTHER_TENANT = define(
            "nop.err.orm.not-allow-process-entity-in-other-tenant", "不允许访问其他租户中的数据", ARG_ENTITY_NAME, ARG_ENTITY_ID,
            ARG_TENANT_ID);

    ErrorCode ERR_ORM_FLUSH_LOOP_COUNT_EXCEED_LIMIT = define("nop.err.orm.flush-loop-count-exceed-limit",
            "flush循环的执行次数超过最大限制");

    ErrorCode ERR_ORM_VISIT_LOOP_COUNT_EXCEED_LIMIT = define("nop.err.orm.visit-loop-count-exceed-limit",
            "实体遍历循环的执行次数超过最大限制");

    ErrorCode ERR_ORM_QUERY_EXAMPLE_PROP_NOT_INITED = define("nop.err.orm.dao.query-example-prop-not-inited",
            "没有设置查询条件，不允许执行查询", ARG_ENTITY_NAME);

    ErrorCode ERR_ORM_ADD_NULL_ELEMENT_TO_COLLECTION = define("nop.err.orm.add-null-element-to-collection",
            "集合元素不允许为null", ARG_OWNER);

    ErrorCode ERR_ORM_COLLECTION_ELEMENT_NOT_ALLOW_MULTIPLE_OWNER = define(
            "nop.err.orm.collection-element-not-allow-multiple-owner",
            "需要先把[{ownerProp}]属性设置为null，然后才能加入到[{collectionName}]集合中", ARG_OWNER_PROP, ARG_COLLECTION_NAME);

    ErrorCode ERR_ORM_ENTITY_IS_READONLY = define("nop.err.orm.entity-is-readonly", "只读对象不允许被修改:{}", ARG_ENTITY);

    ErrorCode ERR_ORM_COLLECTION_IS_READONLY = define("nop.err.orm.collection-is-readonly",
            "只读集合不允许被修改:collectionName={collectionName},owner={}", ARG_COLLECTION_NAME, ARG_OWNER);

    ErrorCode ERR_ORM_COLLECTION_NOT_ALLOW_NULL = define("nop.err.orm.collection-not-allow-null",
            "对象[{entityName}]的集合属性[{propName}]不允许为null");

    ErrorCode ERR_ORM_ENTITY_NOT_IN_SESSION = define("nop.err.orm.entity-not-in-session",
            "对象不属于当前session: entityName={entityName},id={entityId}", ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_COLLECTION_NOT_IN_SESSION = define("nop.err.orm.collection-not-in-session",
            "集合对象不属于当前session:collectionName={entityName},owner={owner}", ARG_COLLECTION_NAME, ARG_OWNER);

    ErrorCode ERR_ORM_SAVE_ENTITY_REPLACE_EXISTING_ENTITY = define("nop.err.orm.save-entity-replace-existing-entity",
            "session中已经存在类型为[{entityName}]，主键为[{entityId}]的实体，不允许插入主键相同的实体", ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_INVALID_COMPOSITE_PK_PART_COUNT = define("nop.err.orm.invalid-composite-pk-part-count",
            "对象[{entityName}]的复合主键[{entityId}]应该由{count}个部分组成", ARG_ENTITY_NAME, ARG_ENTITY_ID, ARG_COUNT);

    ErrorCode ERR_ORM_INVALID_COMPOSITE_PK_PART = define("nop.err.orm.invalid-composite-pk-part",
            "对象[{entityName}]的复合主键[{entityId}]的组成部分[{value}]不是期待的数据类型[{targetType}]", ARG_ENTITY_NAME, ARG_ENTITY_ID,
            ARG_VALUE, ARG_TARGET_TYPE);

    ErrorCode ERR_ORM_SAVE_ENTITY_NOT_TRANSIENT = define("nop.err.orm.save-entity-not-transient",
            "当前实体状态为[{status}]，不允许保存。只有处于TRANSIENT状态的实体才允许保存", ARG_STATUS);

    ErrorCode ERR_ORM_ENTITY_PROP_TYPE_CONVERSION_FAIL = define("nop.err.orm.entity-prop-type-conversion-fail",
            "类型转换失败。实体[{entityName}]的属性[{propName}]的类型为[{targetType}],而值[{value}]的类型为[{srcType}]", ARG_ENTITY_NAME,
            ARG_SRC_TYPE, ARG_TARGET_TYPE, ARG_VALUE);

    ErrorCode ERR_ORM_ENTITY_VERSION_CHANGED = define("nop.err.orm.entity-version-changed",
            "实体对象[{entityName}:{entityId}]上的乐观锁版本发生了改变:{oldVersion}=>{version}", ARG_ENTITY_NAME, ARG_ENTITY_ID,
            ARG_OLD_VERSION, ARG_VERSION);

    ErrorCode ERR_ORM_ENTITY_NOT_CURRENT_REVISION = define("nop.err.orm.not-current-revision",
            "实体对象[{entityName}:{entityId}]的版本[{revEndVer}]不是最新版本", ARG_ENTITY_NAME, ARG_ENTITY_ID, ARG_REV_END_VER);

    ErrorCode ERR_ORM_INVALID_ENTITY_ID = define("nop.err.orm.invalid-entity-id",
            "实体对象[{entityName}]的ID[{entityId}]类型不正确", ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_NOT_IN_SESSION = define("nop.err.orm.not-in-session", "上下文环境中没有打开的ORM会话");

    ErrorCode ERR_ORM_MANDATORY_PROP_IS_NULL = define("nop.err.orm.mandatory-prop-is-null",
            "实体对象[{entityName}:{entityId}]的非空属性[{propName}]为null", ARG_ENTITY_NAME, ARG_ENTITY_ID, ARG_PROP_NAME);

    ErrorCode ERR_ORM_ENTITY_ALREADY_EXISTS = define("nop.err.orm.entity-already-exists",
            "实体对象[{entityName}:{entityId}]已存在，不允许插入重复的记录", ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_ENTITY_REV_VER_IS_LESS_THAN_HIS_VER = define("nop.err.orm.entity-rev-ver-is-less-than-his-ver",
            "实体对象[{entityName}:{entityId}]的新版本号{revVer}必须大于历史版本号{revBeginVer}", ARG_ENTITY_NAME, ARG_ENTITY_ID,
            ARG_REV_VER, ARG_REV_BEGIN_VER);

    ErrorCode ERR_ORM_BEAN_NOT_PROTOTYPE_SCOPE = define("nop.err.orm.bean-not-prototype-scope",
            "Bean的scope必须是prototype:{scope}", ARG_SCOPE);

    ErrorCode ERR_ORM_SQL_PARAM_COUNT_MISMATCH = define("nop.err.orm.sql-param-count-mismatch",
            "SQL语句的参数个数不正确，当前值:{count}，期待值:{expected}", ARG_COUNT, ARG_EXPECTED);

    ErrorCode ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL = define(
            "nop.err.orm.not-support-multiple-query-space-in-one-sql", "一条SQL语句只允许访问一个数据源:sql={},querySpace={}",
            ARG_SQL, ARG_QUERY_SPACE);

    ErrorCode ERR_ORM_UNKNOWN_COLUMN_PROP_ID = define("nop.err.orm.unknown-column-prop-id",
            "实体[{entityName}]上没有定义编号为[{propId}]的列", ARG_ENTITY_NAME, ARG_PROP_ID);

    ErrorCode ERR_ORM_UNKNOWN_COLUMN = define("nop.err.orm.unknown-column", "实体[{entityName}]上没有定义列[{colName}]",
            ARG_ENTITY_NAME, ARG_COL_NAME);

    ErrorCode ERR_ORM_UNKNOWN_COLUMN_CODE = define("nop.err.orm.unknown-column-code",
            "实体[{entityName}]上没有代码为[{colCode}]的列", ARG_ENTITY_NAME, ARG_COL_CODE);

    ErrorCode ERR_ORM_UNKNOWN_PROP = define("nop.err.orm.unknown-column", "实体[{entityName}]上没有定义属性[{propName}]",
            ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_UNKNOWN_PROP_ID = define("nop.err.orm.unknown-prop-id", "实体[{entityName}]上没有定义编号为[{propId}]的列",
            ARG_ENTITY_NAME, ARG_PROP_ID);

    ErrorCode ERR_ORM_NULL_BINDER_FOR_COLUMN = define("nop.err.orm.null-binder-for-column",
            "对象[{entityName}]的列[{colName}]的类型为[{sqlType}],没有找到对应的数据绑定接口", ARG_ENTITY_NAME, ARG_COL_NAME, ARG_SQL_TYPE,
            ARG_DATA_TYPE);

    ErrorCode ERR_ORM_UNSUPPORTED_DATA_TYPE = define("nop.err.orm.unsupported-data-type", "不支持的数据类型:{dataType}",
            ARG_DATA_TYPE);

    ErrorCode ERR_ORM_ENTITY_SET_NO_KEY_PROP = define("nop.err.orm.entity-set-no-key-prop",
            "集合对象[{collectionName}]没有定义keyProp，不支持作为扩展属性访问", ARG_COLLECTION_NAME);

    ErrorCode ERR_ORM_ENTITY_SET_ELEMENT_NOT_KV_TABLE = define("nop.err.orm.entity-set-element-not-kv-table",
            "集合[{collectionName}]的元素不是IOrmKeyValueTable类型，不支持设置动态属性");

    ErrorCode ERR_EQL_UNSUPPORTED_OP = define("nop.err.eql.unsupported-op", "不支持的运算符:{op}", ARG_OP);

    ErrorCode ERR_ORM_ENTITY_NOT_DETACHED = define("nop.err.orm.entity-not-detached",
            "执行attache函数之前，实体对象[{entityName}]已经先从原先的session中evict");

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
            "关联属性表达式[{propPath}]不是[{entityName}]对象的多对一关联属性", ARG_PROP_PATH, ARG_ENTITY_NAME);

    ErrorCode ERR_EQL_INVALID_SQL_TYPE = define("nop.err.eql.invalid-sql-type",
            "未定义的SQL数据类型[{sqlType}]。只允许StdSqlType中定义的常量:{allowedNames}", ARG_SQL_TYPE, ARG_ALLOWED_NAMES);

    ErrorCode ERR_EQL_SELECT_NO_PROJECTIONS = define("nop.err.eql.select-no-projections", "select语句没有指定选择字段列表");

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

    ErrorCode ERR_EQL_NOT_SUPPORT_ILIKE = define("nop.err.eql.not-support-ilike-operator", "数据库不支持ilike运算符", ARG_FUNC_NAME);


    ErrorCode ERR_SQL_UNKNOWN_LIB_PATH = define("nop.err.sql.unknown-lib-path", "sql库文件不存在:{path}", ARG_PATH);

    ErrorCode ERR_SQL_LIB_UNKNOWN_SQL_ITEM = define("nop.err.orm.sql-lib.unknown-sql-item",
            "sql库文件[{path}]中没有定义条目:{sqlItemName}", ARG_PATH, ARG_SQL_ITEM_NAME);

    ErrorCode ERR_SQL_LIB_INVALID_SQL_NAME = define("nop.err.orm.sql-lib.invalid-sql-name",
            "sqlName[{sqlName}]格式不合法，要求的格式为sqlLibName.sqlItemName", ARG_SQL_NAME);

    ErrorCode ERR_SQL_LIB_INVALID_COL_INDEX = define("nop.err.orm.sql-lib.invalid-col-index",
            "sql语句的column配置的index必须在0和1000之间", ARG_SQL_NAME, ARG_INDEX);

    ErrorCode ERR_DAO_PROP_NOT_TO_ONE_RELATION = define("nop.err.orm.dao.prop-not-to-one-relation",
            "实体上的[{entityName}]的属性[{propName}]不是对象引用");

    ErrorCode ERR_ORM_MODEL_DUPLICATE_PROP_ID = define("nop.err.orm.model.duplicate-prop-id",
            "对象模型[{entityName}]的列[{propName}]和[{otherPropName}]的编号都是[{propId}]", ARG_ENTITY_NAME, ARG_PROP_NAME,
            ARG_OTHER_PROP_NAME, ARG_PROP_ID);

    ErrorCode ERR_ORM_MODEL_DUPLICATE_COL_CODE = define("nop.err.orm.model.duplicate-col-code",
            "对象模型[{entityName}]的列[{propName}]和[{otherPropName}]的代码都是[{colCode}]", ARG_ENTITY_NAME, ARG_PROP_NAME,
            ARG_OTHER_PROP_NAME, ARG_COL_CODE);

    ErrorCode ERR_ORM_MODEL_DUPLICATE_PROP = define("nop.err.orm.model.duplicate-prop",
            "对象模型[{entityName}]的属性[{propName}]重复", ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_PROP_ID_IS_RESERVED = define("nop.err.orm.prop-id-is-reserved",
            "对象模型[{entityName}]的属性名不能为id，id为系统保留名称");

    ErrorCode ERR_ORM_MODEL_INVALID_PROP_ID = define("nop.err.orm.model.invalid-prop-id",
            "对象模型[{entityName}]的列[{propName}]的编号必须大于0，小于2000", ARG_ENTITY_NAME, ARG_PROP_NAME, ARG_PROP_ID);

    ErrorCode ERR_ORM_ALIAS_MUST_REF_TO_COLUMN_OR_REFERENCE = define(
            "nop.err.orm.alias-must-ref-to-column-or-reference", "别名[{propName}]必须引用对象模型[{entityName}]中的列或者关联对象",
            ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_MODEL_REF_DEPENDS_CONTAINS_LOOP = define("nop.err.orm.model.ref-depends-contains-loop",
            "对象模型的依赖关系不能包含循环依赖", ARG_LOOP_ENTITY_NAMES);

    ErrorCode ERR_ORM_MODEL_DUPLICATE_ENTITY_SHORT_NAME = define("nop.err.orm.model.duplicate-entity-short-name",
            "实体[{entityName}]的短名称和实体[{otherEntityName}]的短名称冲突", ARG_ENTITY_NAME, ARG_OTHER_ENTITY_NAME);

    ErrorCode ERR_ORM_MODEL_REF_UNKNOWN_ENTITY = define("nop.err.orm.model.ref-unknown-entity",
            "对象模型[{entityName}]的属性[{refName}]引用了未知的对象[{refEntityName}]", ARG_ENTITY_NAME, ARG_REF_NAME,
            ARG_REF_ENTITY_NAME);

    ErrorCode ERR_ORM_MODEL_REF_ENTITY_NO_PROP = define("nop.err.orm.model.ref-entity-no-prop",
            "引用对象[{refEntityName}]上未定义属性[{propName}]", ARG_REF_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_MODEL_REF_ENTITY_PROP_NOT_PRIMARY_KEY = define(
            "nop.err.orm.model.ref-entity-prop-not-primary-key",
            "关联引用的属性[{propName}]不是引用对象[{refEntityName}]的主键，只支持主外键关联", ARG_REF_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_MODEL_JOIN_COLUMN_COUNT_LESS_THAN_PK_COLUMN_COUNT = define(
            "nop.err.orm.model.join-column-count-less-than-pk-column-count",
            "实体[{entityName}]的关联引用属性[{propName}]的关联条件个数小于关联实体主键字段的个数，只支持主外键关联", ARG_ENTITY_NAME, ARG_REF_ENTITY_NAME,
            ARG_PROP_NAME);

    ErrorCode ERR_ORM_MODEL_REF_PROP_NOT_COLUMN = define("nop.err.orm.model.ref-prop-not-column",
            "实体[{entityName}]的关联属性[{propName}]不是数据库中的列，只支持主外键关联", ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_MODEL_RELATION_JOIN_IS_EMPTY = define("nop.err.orm.model.relation-join-is-empty",
            "对象[{entityName]的属性[{refName}]所对应的关联条件为空", ARG_ENTITY_NAME, ARG_REF_NAME);

    ErrorCode ERR_ORM_ENTITY_MODEL_NO_PK = define("nop.err.orm.model.entity-model-no-pk", "对象模型[{entityName}]没有定义主键");

    ErrorCode ERR_ORM_MODEL_REF_JOIN_MUST_ON_COLUMNS_OR_ID = define(
            "nop.err.orm.model.ref-join-must-be-on-columns-or-id", "关联属性[{refName}]的关联条件必须定义在数据列或者主键属性上",
            ARG_ENTITY_NAME, ARG_REF_NAME);

    ErrorCode ERR_ORM_MODEL_REF_JOIN_NO_CONDITION = define("nop.err.orm.model.ref-join-no-condition",
            "对象[{entityName}]上的关联属性[{refName}]的关联条件为空", ARG_ENTITY_NAME, ARG_REF_NAME);

    ErrorCode ERR_ORM_MODEL_ENTITY_NAME_CONFLICTED = define("nop.err.orm.model.entity-name-conflicted",
            "实体名称不能重复:{entityName}", ARG_ENTITY_NAME);

    ErrorCode ERR_ORM_MODEL_MULTIPLE_STATE_PROP = define("nop.err.orm.model.multiple-state-prop",
            "实体[{entityName}]上存在多个字段被标记为状态字段:{propName},{otherPropName}", ARG_ENTITY_NAME, ARG_PROP_NAME,
            ARG_OTHER_PROP_NAME);

    ErrorCode ERR_ORM_MODEL_INVALID_PROP_NAME = define("nop.err.orm.model.invalid-prop-name",
            "实体[{entityName}]的属性名称[{propName}]格式不正确", ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_MODEL_MULTIPLE_VERSION_PROP = define("nop.err.orm.model.multiple-version-prop",
            "实体[{entityName}]上存在多个字段被标记为乐观锁字段:{propName},{otherPropName}", ARG_ENTITY_NAME, ARG_PROP_NAME,
            ARG_OTHER_PROP_NAME);

    ErrorCode ERR_ORM_MODEL_MULTIPLE_LABEL_PROP = define("nop.err.orm.model.multiple-label-prop",
            "实体[{entityName}]上存在多个字段被标记为文本名称字段:{propName},{otherPropName}", ARG_ENTITY_NAME, ARG_PROP_NAME,
            ARG_OTHER_PROP_NAME);

    ErrorCode ERR_ORM_MODEL_INVALID_COLUMN_DOMAIN = define("nop.err.orm.model.invalid-column-domain",
            "实体[{entityName}]上的列[{colName}]上设置的数据域[{domain}]未在模型中定义", ARG_ENTITY_NAME, ARG_COL_NAME, ARG_DOMAIN);

    ErrorCode ERR_ORM_MODEL_COL_NO_STD_SQL_TYPE = define("nop.err.orm.model.col-no-std-sql-type",
            "实体[{entityName}]上的列[{colName}]上没有设置sql数据类型也没有指定数据域");

    ErrorCode ERR_ORM_MODEL_COL_DATA_TYPE_NOT_MATCH_DOMAIN_DEFINITION = define(
            "nop.err.orm.model.col-data-type-not-match-domain-definition",
            "实体[{entityName}]上的列[{colName}]的数据类型定义[{dataType}]与数据域上的类型定义[domainDataType]不一致");

    ErrorCode ERR_ORM_MODEL_UNKNOWN_COMPONENT_PROP = define("nop.err.orm.model.unknown-component-prop",
            "组件对象[{componentClass}]上没有定义属性[{propName}]", ARG_COMPONENT_CLASS, ARG_PROP_NAME);

    ErrorCode ERR_SQL_LIB_CONVERT_RETURN_TYPE_FAIL = define("nop.err.orm.sql-lib.convert-return-type-fail", "结果类型转换错误");

    ErrorCode ERR_ORM_INVALID_DATA_TYPE = define("nop.err.orm.invalid-sql-type", "SQL数据类型格式不正确");

    ErrorCode ERR_ORM_INVALID_FIELD_NAME = define("nop.err.orm.invalid-field-name", "非法的字段名称:{fieldName}",
            ARG_FIELD_NAME);

    ErrorCode ERR_ORM_INVALID_OWNER_NAME = define("nop.err.orm.invalid-owner-name", "非法的owner名称:{owner}", ARG_OWNER);

    ErrorCode ERR_ORM_INVALID_FUNC_NAME = define("nop.err.orm.invalid-func-name", "非法的函数名:{funcName}", ARG_FUNC_NAME);

    ErrorCode ERR_ORM_QUERY_DIM_FIELDS_MISMATCH = define("nop.err.orm.dim-fields-mismatch",
            "{sourceName}和{subSourceName}的维度字段的个数不匹配", ARG_SOURCE_NAME, ARG_SUB_SOURCE_NAME);

    ErrorCode ERR_ORM_QUERY_NOT_ALLOW_GROUP_BY = define("nop.err.orm.query-not-allow-group-by",
            "查询列表中包含关联子表字段时不支持group by");

    ErrorCode ERR_ORM_INVALID_ENTITY_NAME = define("nop.err.orm.invalid-entity-name", "非法的对象名:{entityName}",
            ARG_ENTITY_NAME);

    ErrorCode ERR_ORM_QUERY_NO_DIM_FIELDS = define("nop.err.orm.query-no-dim-fields", "查询对象没有指定dimFields属性",
            ARG_SOURCE_NAME);

    ErrorCode ERR_ORM_QUERY_INVALID_JOIN = define("nop.err.orm.query-invalid-join", "不支持的join类型");

    ErrorCode ERR_ORM_QUERY_TIMEOUT = define("nop.err.orm.query-timeout", "执行查询超时");

    ErrorCode ERR_ORM_COPY_ENTITY_PROP_NOT_COLLECTION = define("nop.err.orm.copy-entity-prop-not-collection",
            "值的类型是[{propClass}]，不能复制到实体的集合属性[{propName}]上", ARG_PROP_NAME, ARG_PROP_CLASS);

    ErrorCode ERR_ORM_NOT_SINGLETON_SET = define("nop.err.orm.not-singleton-set",
            "集合[{collectionName}]中的元素个数不是1，不是单例集合", ARG_COLLECTION_NAME, ARG_OWNER);
}