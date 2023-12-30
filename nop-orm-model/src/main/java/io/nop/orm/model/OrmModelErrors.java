/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface OrmModelErrors {
    String ARG_ENTITY_NAME = "entityName";
    String ARG_ENTITY_ID = "entityId";
    String ARG_PROP_NAME = "propName";
    String ARG_TAG = "tag";

    String ARG_ALLOWED_NAMES = "allowedNames";

    String ARG_DOMAIN_DATA_TYPE = "domainDataType";

    String ARG_OTHER_ENTITY_NAME = "otherEntityName";

    String ARG_OTHER_PROP_NAME = "otherPropName";

    String ARG_LOOP_ENTITY_NAMES = "loopEntityNames";

    String ARG_REF_NAME = "refName";
    String ARG_REF_ENTITY_NAME = "refEntityName";

    String ARG_OTHER_LOC = "otherLoc";

    String ARG_PROP_ID = "propId";

    String ARG_COL_CODE = "colCode";

    String ARG_COMPONENT_CLASS = "componentClass";

    String ARG_COL_NAME = "colName";

    String ARG_DOMAIN = "domain";

    String ARG_DATA_TYPE = "dataType";

    String ARG_TABLE_NAME = "tableName";
    String ARG_COLLECTION_NAME = "collectionName";
    String ARG_TENANT_ID = "tenantId";

    String ARG_ARG_NAME = "argName";

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

    ErrorCode ERR_ORM_INVALID_DATA_TYPE = define("nop.err.orm.model.invalid-sql-type", "SQL数据类型格式不正确");

    ErrorCode ERR_ORM_TENANT_ID_NOT_ALLOW_CHANGE = define("nop.err.orm.tenant-id-not-allow-change", "租户ID不允许被改变",
            ARG_ENTITY_NAME, ARG_ENTITY_ID, ARG_TENANT_ID);

    ErrorCode ERR_ORM_UNKNOWN_ENTITY_NAME = define("nop.err.orm.model.unknown-entity-name", "未定义的对象名:{entityName}",
            ARG_ENTITY_NAME);

    ErrorCode ERR_ORM_UNKNOWN_ENTITY_MODEL_FOR_TABLE = define("nop.err.orm.model.unknown-entity-model-for-table",
            "没有定义实体模型对应于数据库表:{tableName}", ARG_TABLE_NAME);

    ErrorCode ERR_ORM_UNKNOWN_COLLECTION_NAME = define("nop.err.orm.model.unknown-collection-name",
            "未定义的对象集合:{collectionName}", ARG_COLLECTION_NAME);

    ErrorCode ERR_ORM_UNKNOWN_COLUMN_PROP_ID = define("nop.err.orm.model.unknown-column-prop-id",
            "实体[{entityName}]上没有定义编号为[{propId}]的列", ARG_ENTITY_NAME, ARG_PROP_ID);

    ErrorCode ERR_ORM_UNKNOWN_COLUMN = define("nop.err.orm.model.unknown-column", "实体[{entityName}]上没有定义列[{colName}]",
            ARG_ENTITY_NAME, ARG_COL_NAME);

    ErrorCode ERR_ORM_UNKNOWN_COLUMN_CODE = define("nop.err.orm.model.unknown-column-code",
            "实体[{entityName}]上没有代码为[{colCode}]的列", ARG_ENTITY_NAME, ARG_COL_CODE);

    ErrorCode ERR_ORM_UNKNOWN_PROP = define("nop.err.orm.model.unknown-prop", "实体[{entityName}]上没有定义属性[{propName}]",
            ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_UNKNOWN_PROP_ID = define("nop.err.orm.model.unknown-prop-id", "实体[{entityName}]上没有定义编号为[{propId}]的列",
            ARG_ENTITY_NAME, ARG_PROP_ID);

    ErrorCode ERR_ORM_NO_COL_WITH_TAG =
            define("nop.err.orm.model.no-col-with-tag", "实体[{entityName}]中没有定义具有标签[{tag}]的列",
                    ARG_ENTITY_NAME, ARG_TAG);

    ErrorCode ERR_ORM_COMPUTE_PROP_NO_GETTER =
            define("nop.err.orm.model.compute-prop-no-getter",
                    "实体[{entityName}]的计算属性[{propName}]没有定义getter，不支持获取值", ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_COMPUTE_PROP_NO_SETTER =
            define("nop.err.orm.model.compute-prop-no-setter",
                    "实体[{entityName}]的计算属性[{propName}]没有定义setter，不支持设置值", ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_ORM_UNKNOWN_COMPUTE_PROP_ARG = define("nop.err.orm.unknown-compute-prop-arg",
            "对象[{entityName}]的计算属性[{propName}]不支持参数[{argName}]", ARG_ENTITY_NAME, ARG_PROP_NAME, ARG_ARG_NAME);


}
