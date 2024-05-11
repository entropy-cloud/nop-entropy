/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface BizErrors {
    String ARG_BIZ_OBJ_NAME = "bizObjName";

    String ARG_OBJ_LABEL = "objLabel";

    String ARG_META_PATH = "metaPath";

    String ARG_ACTION_NAME = "actionName";

    String ARG_CLASS_NAME = "className";

    String ARG_PROP_NAME = "propName";

    String ARG_PROP_DISPLAY_NAME = "propDisplayName";

    String ARG_DISPLAY_NAME = "displayName";

    String ARG_RELATION = "relation";

    String ARG_EN_NAME = "enName";

    String ARG_VALUE = "value";

    String ARG_VALUE_TYPE = "valueType";

    String ARG_VALUE_NAME = "valueName";

    String ARG_SELECTION_ID = "selectionId";

    String ARG_OPTION_VALUE = "optionValue";

    String ARG_DICT = "dict";

    String ARG_MAX_LENGTH = "maxLength";
    String ARG_PARAM_NAME = "paramName";

    String ARG_FILTER_OP = "filterOp";
    String ARG_ALLOW_FILTER_OP = "allowFilterOp";

    String ARG_COUNT = "count";
    String ARG_MAX_COUNT = "maxCount";

    String ARG_ID = "id";
    String ARG_ENTITY_NAME = "entityName";
    String ARG_KEY = "key";

    String ARG_FILTER_VALUE = "filterValue";
    String ARG_PROP_VALUE = "propValue";

    String ARG_DICT_NAME = "dictName";

    String ARG_VAR_NAME = "varName";
    String ARG_ATTR_NAME = "attrName";

    String ARG_PROP_NAMES = "propNames";

    String ARG_REF_ENTITY_NAME = "refEntityName";

    String ARG_REF_ENTITY = "refEntity";

    ErrorCode ERR_BIZ_INVALID_BIZ_OBJ_NAME = define("nop.err.biz.invalid-biz-obj-name", "非法的bizObjName: {bizObjName}",
            ARG_BIZ_OBJ_NAME);

    ErrorCode ERR_BIZ_INVALID_ACTION_NAME = define("nop.err.biz.invalid-action-name",
            "非法的操作函数名: {actionName}。对象名和方法名应该通过__来分隔", ARG_ACTION_NAME);

    ErrorCode ERR_BIZ_NO_MANDATORY_PARAM = define("nop.err.biz.no-param-id",
            "对象[{bizObjName}]的方法[{actionName}]要求非空的参数[{paramName}]", ARG_BIZ_OBJ_NAME, ARG_ACTION_NAME, ARG_PARAM_NAME);

    ErrorCode ERR_BIZ_MISSING_META_FILE_FOR_OBJ = define("nop.err.biz.missing-meta-file-for-obj",
            "对象[{bizObjName}]缺少元数据定义文件:{metaPath}", ARG_BIZ_OBJ_NAME, ARG_META_PATH);

    ErrorCode ERR_BIZ_UNKNOWN_BIZ_OBJ_NAME = define("nop.err.biz.unknown-biz-obj-name", "未定义的业务对象:{bizObjName}，请注意对象名和方法名之间是两个下划线",
            ARG_BIZ_OBJ_NAME);

    ErrorCode ERR_BIZ_UNKNOWN_ACTION = define("nop.err.biz.unknown-action", "对象[{bizObjName}]不支持操作[{actionName}]",
            ARG_BIZ_OBJ_NAME, ARG_ACTION_NAME);

    ErrorCode ERR_BIZ_NO_BIZ_MODEL_ANNOTATION = define("nop.err.biz.no-biz-model-annotation",
            "[{className}]上没有@BizModel注解", ARG_CLASS_NAME);

    ErrorCode ERR_BIZ_NO_OBJ_META = define("nop.err.biz.no-obj-meta", "对象[{bizObjName}]缺少objMeta模型信息");

    ErrorCode ERR_BIZ_STATE_MACHINE_NO_STATE_PROP = define("nop.err.biz.state-machine-no-state-prop",
            "状态机没有设置stateProp属性");

    ErrorCode ERR_BIZ_NO_STATE_MACHINE = define("nop.err.biz.no-state-machine",
            "对象[{bizObjName}]没有定义关联的状态机，不支持触发状态迁移函数");

    ErrorCode ERR_BIZ_UNKNOWN_PROP = define("nop.err.biz.unknown-prop", "对象[{bizObjName}]没有定义属性[{propName}]",
            ARG_BIZ_OBJ_NAME, ARG_PROP_NAME, ARG_DISPLAY_NAME);

    ErrorCode ERR_BIZ_NOT_ALLOW_JOIN_PROP_IN_REF_ENTITY = define("nop.err.biz.not-allow-join-prop-in-ref-entity",
            "对象[{bizObjName}]的关联属性[{propName}]中不允许包含主实体的关联字段", ARG_BIZ_OBJ_NAME, ARG_PROP_NAME);

    ErrorCode ERR_BIZ_UNKNOWN_RELATION = define("nop.err.biz.unknown-relation",
            "对象[{bizObjName}]的属性[{propName}]上标记的关联属性[{relation}]不存在",
            ARG_BIZ_OBJ_NAME, ARG_PROP_NAME, ARG_DISPLAY_NAME, ARG_RELATION);

    ErrorCode ERR_BIZ_REF_ENTITY_OWNER_NOT_MATCH = define("nop.err.biz.ref-entity-owner-not-match",
            "id为[{id}]的对象[{entityName}]不属于当前关联对象", ARG_ID, ARG_ENTITY_NAME);

    ErrorCode ERR_BIZ_MANDATORY_PROP_IS_EMPTY = define("nop.err.biz.mandatory-prop-is-empty", "属性[{propName}]的值为空",
            ARG_BIZ_OBJ_NAME, ARG_PROP_NAME, ARG_DISPLAY_NAME);

    ErrorCode ERR_BIZ_PROP_TYPE_CONVERT_FAIL = define("nop.err.biz.prop-type-convert-fail",
            "属性[{propName}]的值[{value}]不能被转换为[{valueType}]类型", ARG_PROP_NAME, ARG_VALUE, ARG_VALUE_TYPE);

    ErrorCode ERR_BIZ_UNKNOWN_SELECTION = define("nop.err.biz.unknown-selection",
            "对象[{bizObjName}]没有定义字段集合[{selectionId}]", ARG_BIZ_OBJ_NAME, ARG_SELECTION_ID);

    ErrorCode ERR_BIZ_PROP_IS_NOT_COLLECTION = define("nop.err.biz.prop-is-not-collection", "属性[{propName}]不是集合类型",
            ARG_PROP_NAME);

    ErrorCode ERR_BIZ_PROP_IS_NOT_MAP = define("nop.err.biz.prop-is-not-map", "属性[{propName}]不是Map类型", ARG_PROP_NAME);

    ErrorCode ERR_BIZ_INVALID_DICT_OPTION = define("nop.err.biz.invalid-dict-option", "非法的字典项:{optionValue}", ARG_DICT,
            ARG_OPTION_VALUE);

    ErrorCode ERR_BIZ_PROP_EXCEED_MAX_LENGTH = define("nop.err.biz.prop-exceed-max-length",
            "属性[{propName}]的长度超过最大长度限制,最大值为{maxLength}", ARG_PROP_NAME, ARG_MAX_LENGTH);

    ErrorCode ERR_BIZ_EMPTY_DATA_FOR_SAVE = define("nop.err.biz.empty-data-for-save", "新建操作的data参数不能为空");

    ErrorCode ERR_BIZ_EMPTY_DATA_FOR_UPDATE = define("nop.err.biz.empty-data-for-update", "修改操作的data参数不能为空");

    ErrorCode ERR_BIZ_OBJECT_NOT_SUPPORT_ACTION = define("nop.err.biz.object-not-support-action",
            "业务对象[{bizObjName}]不支持函数[{actionName}]", ARG_BIZ_OBJ_NAME, ARG_ACTION_NAME);

    ErrorCode ERR_BIZ_NOT_ALLOW_WRITE_PROP =
            define("nop.err.biz.not-allow-write-prop", "不允许修改属性:{propName}", ARG_PROP_NAME);
    ErrorCode ERR_BIZ_ACTION_ARG_NOT_FIELD_SELECTION = define("nop.err.biz.action-arg-not-field-selection",
            "通过thisObj来调用业务对象[{bizObjName}]的业务处理函数[{actionName}]时，传入的参数不是FieldSelectionBean类型", ARG_BIZ_OBJ_NAME,
            ARG_ACTION_NAME);

    ErrorCode ERR_BIZ_ACTION_ARG_NOT_SERVICE_CONTEXT = define("nop.err.biz.action-arg2-not-service-context",
            "通过thisObj来调用业务对象[{bizObjName}]的业务处理函数[{actionName}]时，传入的参数不是IServiceContext类型", ARG_BIZ_OBJ_NAME,
            ARG_ACTION_NAME);

    ErrorCode ERR_BIZ_QUERY_NOT_SUPPORT_COMPARE_WITH_VALUE_PROP = define(
            "nop.err.biz.query-not-support-query-with-value-prop", "查询条件不支持将字段[{propName}]和字段[{valueName}]进行比较",
            ARG_PROP_NAME, ARG_VALUE_NAME);

    ErrorCode ERR_BIZ_UNKNOWN_QUERY_PROP = define("nop.err.biz.unknown-query-prop", "未定义的查询字段:{propName}",
            ARG_PROP_NAME);

    ErrorCode ERR_BIZ_PROP_NOT_SUPPORT_QUERY = define("nop.err.biz.prop-not-support-query", "不支持通过字段[{propName}]进行查询");

    ErrorCode ERR_BIZ_PROP_NOT_SUPPORT_FILTER_OP = define("nop.err.biz.prop-not-support-filter-op",
            "查询字段只允许以下查询运算符:{allowFilterOp}, 不支持{filterOp}", ARG_ALLOW_FILTER_OP, ARG_FILTER_OP);

    ErrorCode ERR_BIZ_QUERY_IN_OP_TOO_MANY_VALUE = define("nop.err.biz.query-in-op-too-many-value",
            "in查询条件的参数个数[{count}]超过了最大允许查询参数个数[{maxCount}]", ARG_COUNT, ARG_MAX_COUNT);

    ErrorCode ERR_BIZ_ENTITY_ALREADY_EXISTS = define("nop.err.biz.entity-already-exists",
            "id为[{id}]的对象[{entityName}]已经存在，不允许重复保存对象", ARG_ID, ARG_ENTITY_NAME);

    ErrorCode ERR_BIZ_ENTITY_WITH_SAME_KEY_ALREADY_EXISTS = define("nop.err.biz.entity-already-exists",
            "当前为[{key}]的数据已存在，请确保字段名为[{displayName}]的数据唯一", ARG_KEY, ARG_DISPLAY_NAME, ARG_ENTITY_NAME);

    ErrorCode ERR_BIZ_PROP_VALUE_NOT_MATCH_FILTER_CONDITION = define(
            "nop.err.biz.prop-value-not-match-filter-condition",
            "属性[{propName}]的值[{propValue}]与预定义的对象匹配条件[{filterValue}]不一致", ARG_PROP_NAME, ARG_PROP_VALUE,
            ARG_FILTER_VALUE);

    ErrorCode ERR_BIZ_ENTITY_NOT_MATCH_FILTER_CONDITION = define("nop.err.biz.entity-not-match-filter-condition",
            "id为[{id}]的实体[{bizObjName}]不满足对象匹配条件", ARG_BIZ_OBJ_NAME, ARG_ID);

    ErrorCode ERR_BIZ_ENTITY_NOT_SUPPORT_LOGICAL_DELETE = define("nop.err.biz.entity-not-support-logical-delete",
            "对象[{bizObjName}]不支持逻辑删除", ARG_BIZ_OBJ_NAME);

    ErrorCode ERR_BIZ_INVALID_OBJ_DICT_NAME = define("nop.err.biz.invalid-obj-dict-name",
            "[{dictName}]不是合法的对象字典名称，需要符合格式obj/[bizObjName]__[actionName]", ARG_DICT_NAME);

    ErrorCode ERR_BIZ_OBJ_NO_DICT_TAG = define("nop.err.biz.obj-no-dict-tag", "对象[{bizObjName}]没有标注dict标签，不能作为字典表使用");

    ErrorCode ERR_BIZ_PROP_NOT_SORTABLE = define("nop.err.biz.prop-not-sortable",
            "对象[{bizObjName}]的属性[{propName}]不支持排序", ARG_BIZ_OBJ_NAME, ARG_PROP_NAME);

    ErrorCode ERR_BIZ_OBJ_NO_TREE_PARENT_PROP = define("nop.err.biz.obj-no-tree-parent-prop",
            "对象[{bizObjName}]没有标记为parent的属性，不能执行树形结构相关的方法", ARG_BIZ_OBJ_NAME);

    ErrorCode ERR_BIZ_NOT_ALLOW_DELETE_PARENT_WHEN_CHILDREN_IS_NOT_EMPTY = define(
            "nop.err.biz.not-allow-delete-parent-when-children-is-not-empty",
            "类型为[{bizObjName}]的对象[{objLabel}]的子节点不为空，不允许删除", ARG_BIZ_OBJ_NAME, ARG_OBJ_LABEL);

    ErrorCode ERR_BIZ_INVALID_META_PATH =
            define("nop.err.biz.invalid-meta-path", "meta文件的后缀名必须是xmeta:{metaPath}",
                    ARG_META_PATH);


    ErrorCode ERR_BIZ_PROP_NOT_MANY_TO_MANY_REF =
            define("nop.err.biz.prop-not-many-to-many-ref", "属性[{propName}]不是多对多关联属性",
                    ARG_BIZ_OBJ_NAME, ARG_PROP_NAME);

    ErrorCode ERR_BIZ_OPERATION_NO_IMPL_ACTION =
            define("nop.err.biz.operation-no-impl-action", "服务函数[{operationName}]没有提供实现函数");

    ErrorCode ERR_BIZ_NOT_ALLOWED_LEFT_JOIN_PROPS =
            define("nop.err.biz.not-allowed-left-join-props", "不允许如下属性使用左连接:{propNames}", ARG_PROP_NAMES);

    ErrorCode ERR_BIZ_TOO_MANY_LEFT_JOIN_PROPS_IN_QUERY =
            define("nop.err.biz.too-many-left-join-props-in-query", "查询对象中包含太多的左连接属性设置:{propNames}", ARG_PROP_NAMES);

    ErrorCode ERR_BIZ_UNKNOWN_REF_ENTITY_WITH_PROP =
            define("nop.err.biz.unknown-ref-entity", "引用对象[{bizObjName}]中不存在属性[{propName}]的值为[{propValue}]记录",
                    ARG_BIZ_OBJ_NAME, ARG_PROP_NAME, ARG_PROP_VALUE);

    ErrorCode ERR_BIZ_NOT_SUPPORT_TREE_MODEL =
            define("nop.err.biz.not-support-tree-model", "实体[{bizObjName}]没有定义Tree结构模型", ARG_ENTITY_NAME);

    ErrorCode ERR_BIZ_TREE_ENTITY_NO_PARENT_PROP =
            define("nop.err.biz.tree-entity-no-parent-prop", "树形结构对象[{bizObjName}]没有定义parentProp属性", ARG_BIZ_OBJ_NAME);

    ErrorCode ERR_BIZ_NOT_ALLOW_DELETE_ENTITY_WHEN_REF_EXISTS =
            define("nop.err.biz.not-allow-delete-entity-when-ref-exists", "不允许删除id为[{id}]的实体[{bizObjName}]，因为它被[{refEntityName}]中的实体所引用",
                    ARG_ID, ARG_BIZ_OBJ_NAME, ARG_REF_ENTITY_NAME);
}