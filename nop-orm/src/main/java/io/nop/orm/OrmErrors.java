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

    String ARG_ARG_NAME = "argName";

    String ARG_ELM_OWNER = "elmOwner";

    String ARG_TAG = "tag";

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

    String ARG_FRAGMENT_ID = "fragmentId";

    String ARG_PERMISSION = "permission";

    String ARG_ROLES = "roles";

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

    ErrorCode ERR_ORM_MISSING_TENANT_ID_IN_CONTEXT = define("nop.err.orm.missing-tenant-id-in-context",
            "访问实体对象[{entityName}:{entityId}]时上下文环境中没有设置租户", ARG_ENTITY_NAME, ARG_ENTITY_ID);

    ErrorCode ERR_ORM_ENTITY_ID_NOT_SET = define("nop.err.orm.entity-id-not-set",
            "实体的主键字段[{propName}]没有被设置，而且主键字段上没有seq标签，无法自动生成",
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


    ErrorCode ERR_ORM_UNSUPPORTED_DATA_TYPE = define("nop.err.orm.unsupported-data-type", "不支持的数据类型:{dataType}",
            ARG_DATA_TYPE);

    ErrorCode ERR_ORM_ENTITY_SET_NO_KEY_PROP = define("nop.err.orm.entity-set-no-key-prop",
            "集合对象[{collectionName}]没有定义keyProp，不支持作为扩展属性访问", ARG_COLLECTION_NAME);

    ErrorCode ERR_ORM_ENTITY_SET_ELEMENT_NOT_KV_TABLE = define("nop.err.orm.entity-set-element-not-kv-table",
            "集合[{collectionName}]的元素不是IOrmKeyValueTable类型，不支持设置动态属性");


    ErrorCode ERR_ORM_ENTITY_NOT_DETACHED = define("nop.err.orm.entity-not-detached",
            "执行attache函数之前，实体对象[{entityName}]已经先从原先的session中evict");

    ErrorCode ERR_SQL_UNKNOWN_LIB_PATH = define("nop.err.sql.unknown-lib-path", "sql库文件不存在:{path}", ARG_PATH);

    ErrorCode ERR_SQL_LIB_UNKNOWN_SQL_ITEM = define("nop.err.orm.sql-lib.unknown-sql-item",
            "sql库文件[{path}]中没有定义条目:{sqlItemName}", ARG_PATH, ARG_SQL_ITEM_NAME);

    ErrorCode ERR_SQL_LIB_INVALID_SQL_NAME = define("nop.err.orm.sql-lib.invalid-sql-name",
            "sqlName[{sqlName}]格式不合法，要求的格式为sqlLibName.sqlItemName", ARG_SQL_NAME);

    ErrorCode ERR_SQL_LIB_INVALID_COL_INDEX = define("nop.err.orm.sql-lib.invalid-col-index",
            "sql语句的column配置的index必须在0和1000之间", ARG_SQL_NAME, ARG_INDEX);

    ErrorCode ERR_DAO_PROP_NOT_TO_ONE_RELATION = define("nop.err.orm.dao.prop-not-to-one-relation",
            "实体上的[{entityName}]的属性[{propName}]不是对象引用");

    ErrorCode ERR_SQL_LIB_CONVERT_RETURN_TYPE_FAIL = define("nop.err.orm.sql-lib.convert-return-type-fail", "结果类型转换错误");


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

    ErrorCode ERR_ORM_UNKNOWN_FRAGMENT = define("nop.err.orm.unknown-fragment",
            "未知的SQL片段:{fragmentId}", ARG_FRAGMENT_ID);

    ErrorCode ERR_ORM_NO_PERMISSION_FOR_SQL = define("nop.err.orm.no-permission-for-sql",
            "没有访问权限", ARG_PERMISSION, ARG_SQL_NAME);

    ErrorCode ERR_ORM_ENTITY_NO_UPDATE_TIME_COL = define("nop.err.orm.nop-update-time-col",
            "实体[{entityName}]没有定义修改时间字段", ARG_ENTITY_NAME);

    ErrorCode ERR_ORM_INVALID_DAO_PATH = define("nop.err.orm.nop-update-time-col",
            "实体[{entityName}]没有定义修改时间字段", ARG_ENTITY_NAME);

    ErrorCode ERR_ORM_ENTITY_NO_CONTENT_PROP =
            define("nop.err.orm.entity-no-content-prop",
                    "实体模型[{entityName}]没有定义标记为content的字段", ARG_ENTITY_NAME);

    ErrorCode ERR_ORM_DUPLICATE_SHORT_ENTITY_NAME =
            define("nop.err.orm.duplicate-short-entity-name", "实体名[{entityName}]和实体名[{otherEntityName}]的短名字重复",
                    ARG_ENTITY_NAME, ARG_OTHER_ENTITY_NAME);
}