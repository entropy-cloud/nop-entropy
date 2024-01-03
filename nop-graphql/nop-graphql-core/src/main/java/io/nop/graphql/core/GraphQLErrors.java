/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface GraphQLErrors {
    String ARG_NAME = "name";
    String ARG_DIRECTIVE = "directive";

    String ARG_MAX_DEPTH = "maxDepth";

    String ARG_LOCATION = "location";

    String ARG_LOADER_NAME = "loaderName";

    String ARG_OBJ_NAME = "objName";
    String ARG_FIELD_NAME = "fieldName";
    String ARG_ALLOWED_NAMES = "allowedNames";

    String ARG_PARENT_NAME = "parentName";

    String ARG_TYPE_NAME = "typeName";

    String ARG_AST_NODE = "astNode";

    String ARG_VAR_NAME = "varName";

    String ARG_FRAGMENT_NAME = "fragmentName";

    String ARG_ARG_NAME = "argName";

    String ARG_OLD_LOC = "oldLoc";

    String ARG_OBJ_TYPE = "objType";

    String ARG_TYPE = "type";

    String ARG_SELECTION_SET = "selectionSet";

    String ARG_LEVEL = "level";

    String ARG_OLD_TYPE = "oldType";

    String ARG_METHOD_NAME = "methodName";

    String ARG_RETURN_TYPE = "returnType";

    String ARG_OPERATION_NAME = "operationName";
    String ARG_OPERATION_TYPE = "operationType";

    String ARG_TRY_METHOD = "tryMethod";
    String ARG_CANCEL_METHOD = "cancelMethod";

    String ARG_EXPECTED_OPERATION_TYPE = "expectedOperationType";

    String ARG_ACTION_NAME = "actionName";

    String ARG_EXPECTED_TYPE = "expectedType";

    String ARG_METHOD = "method";
    String ARG_OLD_METHOD = "oldMethod";

    String ARG_BIZ_OBJ_NAME = "bizObjName";
    String ARG_CLASS = "class";
    String ARG_OTHER_CLASS = "otherClass";
    String ARG_OLD_CLASS = "oldClass";

    String ARG_PATH_A = "pathA";
    String ARG_PATH_B = "pathB";

    String ARG_PROP_NAME = "propName";
    String ARG_VALUE_PROP_NAME = "valuePropName";

    String ARG_DIRECTIVE_NAME = "directiveName";

    String ARG_DICT_NAME = "dictName";

    String ARG_MAX = "max";

    ErrorCode ERR_GRAPHQL_PARSE_INVALID_ARG_NAME = define("nop.err.graphql.parse.invalid-arg-name", "参数名不合法：{name}",
            ARG_NAME);

    ErrorCode ERR_GRAPHQL_PARSE_INVALID_VAR_NAME = define("nop.err.graphql.parse.invalid-var-name", "变量名不合法:{name}",
            ARG_NAME);

    ErrorCode ERR_GRAPHQL_PARSE_UNEXPECTED_CHAR = define("nop.err.graphql.parse.unexpected-char", "非法的子符");

    ErrorCode ERR_GRAPHQL_PARSE_EXCEED_MAX_LENGTH = define("nop.err.graphql.parse.exceed-max-length",
            "GraphQL查询语句过长，超过长度限制");

    ErrorCode ERR_GRAPHQL_QUERY_EXCEED_MAX_DEPTH = define("nop.err.graphql.query.exceed-max-depth",
            "GraphQL查询语句嵌套层次过多，超过最大深度限制。最大深度为{maxDepth}", ARG_MAX_DEPTH);

    ErrorCode ERR_GRAPHQL_ARG_MAX_MUST_BE_POSITIVE = define("nop.err.graphql.arg-max-must-be-positive", "参数max必须为正整数",
            ARG_MAX);

    ErrorCode ERR_GRAPHQL_PARSE_EMPTY_STRING = define("nop.err.graphql.parse.empty-strign", "GraphQL查询语句为空");

    ErrorCode ERR_GRAPHQL_UNKNOWN_DIRECTIVE = define("nop.err.graphql.unknown-directive", "GraphQL扩展未定义",
            ARG_DIRECTIVE);

    ErrorCode ERR_GRAPHQL_NOT_ALLOW_DIRECTIVE_AT_LOCATION = define("nop.err.graphql.not-allow-directive-at-location",
            "GraphQL扩展[{directive}]不允许出现在位置[{location}]", ARG_DIRECTIVE, ARG_LOCATION);

    ErrorCode ERR_GRAPHQL_FIELD_NAME_DUPLICATED = define("nop.err.graphql.field-name-duplicated",
            "GraphQL的字段名不允许重复:{fieldName},parent={parentName}", ARG_FIELD_NAME, ARG_PARENT_NAME);

    ErrorCode ERR_GRAPHQL_UNKNOWN_LOADER = define("nop.err.graphql.unknown-loader", "未定义的Loader:{loaderName}",
            ARG_LOADER_NAME);

    ErrorCode ERR_GRAPHQL_DUPLICATED_LOADER = define("nop.err.graphql.duplicated-loader", "Loader名称不允许重复:{loaderName}",
            ARG_LOADER_NAME);

    ErrorCode ERR_GRAPHQL_UNDEFINED_OBJECT = define("nop.err.graphql.undefined-object", "未定义的对象:{objName}",
            ARG_OBJ_NAME);

    ErrorCode ERR_GRAPHQL_UNDEFINED_TYPE = define("nop.err.graphql.undefined-type", "未定义的类型:{typeName}", ARG_TYPE_NAME);

    ErrorCode ERR_GRAPHQL_UNDEFINED_FIELD = define("nop.err.graphql.undefined-field",
            "对象[{objName}]上没有定义字段[{fieldName}]", ARG_OBJ_NAME, ARG_FIELD_NAME);

    ErrorCode ERR_GRAPHQL_UNDEFINED_FIELD_ARG =
            define("nop.err.graphql.undefined-field-arg",
                    "对象[{objName}]的属性[{fieldName}]没有定义参数[{argName}]", ARG_OBJ_NAME, ARG_FIELD_NAME, ARG_ARG_NAME);

    ErrorCode ERR_GRAPHQL_MISSING_FIELD_ARG =
            define("nop.err.graphql.missing-field-arg",
                    "对象[{objName}]的属性[{fieldName}]要求必须传入参数[{argName}]", ARG_OBJ_NAME, ARG_FIELD_NAME, ARG_ARG_NAME);

    ErrorCode ERR_GRAPHQL_FIELD_NULL_ARG =
            define("nop.err.graphql.field-null-arg",
                    "字段[{fieldName}]的参数[{argName}]不允许为空",
                    ARG_FIELD_NAME, ARG_ARG_NAME);

    ErrorCode ERR_GRAPHQL_UNDEFINED_OPERATION = define("nop.err.graphql.undefined-operation",
            "schema[{operationType}]中没有定义操作:{operationName}", ARG_OPERATION_NAME, ARG_OPERATION_TYPE);

    ErrorCode ERR_GRAPHQL_UNSUPPORTED_AST = define("nop.err.graphql.unsupported-ast", "目前不支持的语法节点：{astNode}",
            ARG_AST_NODE);

    ErrorCode ERR_GRAPHQL_FIELD_COMPLEX_TYPE_NO_SELECTION = define("nop.err.graphql.field-complex-type-no-selection",
            "对象[{objName}]的字段[{fieldName}]不是简单类型，必须指定需要返回的字段集合");

    ErrorCode ERR_GRAPHQL_UNKNOWN_VAR = define("nop.err.graphql.unknown-var", "未定义的变量:{varName}", ARG_VAR_NAME);

    ErrorCode ERR_GRAPHQL_UNKNOWN_FRAGMENT = define("nop.err.graphql.unknown-fragment", "未定义的GraphQL片段:{fragmentName}",
            ARG_FRAGMENT_NAME);

    ErrorCode ERR_GRAPHQL_INVALID_FRAGMENT = define("nop.err.graphql.invalid-fragment", "非法的GraphQL片段:{fragmentName}",
            ARG_FRAGMENT_NAME);

    ErrorCode ERR_GRAPHQL_DOC_OPERATION_SIZE_NOT_ONE = define("nop.err.graphql.doc-operation-size-not-one",
            "graphql请求文本中只能包含唯一一个操作语句");

    ErrorCode ERR_GRAPHQL_DOC_NOT_OPERATION = define("nop.err.graphql.doc-not-operation",
            "graphql请求不是query/mutation/subscription等操作指令");

    ErrorCode ERR_GRAPHQL_UNKNOWN_ARG_FOR_DIRECTIVE = define("nop.err.graphql.unknown-arg-for-directive",
            "directive[{directive}]的参数[{argName}]未定义", ARG_DIRECTIVE, ARG_ARG_NAME);

    ErrorCode ERR_GRAPHQL_DUPLICATE_OBJ_DEF = define("nop.err.graphql.duplicate-obj-def", "对象定义已存在:{objName}",
            ARG_OBJ_NAME, ARG_OLD_LOC);

    ErrorCode ERR_GRAPHQL_DUPLICATE_DIRECTIVE_DEF = define("nop.err.graphql.duplicate-directive-def",
            "指令定义已存在:{directiveName}", ARG_DIRECTIVE_NAME, ARG_OLD_LOC);

    ErrorCode ERR_GRAPHQL_DUPLICATE_TYPE_DEF = define("nop.err.graphql.duplicate-type-def", "类型定义已存在:{typeName}",
            ARG_TYPE_NAME, ARG_OLD_LOC);

    ErrorCode ERR_GRAPHQL_DUPLICATE_FIELD_DEF = define("nop.err.graphql.duplicate-field-def",
            "对象[{objName}]中出现重复定义的字段[{fieldName}]", ARG_OBJ_NAME, ARG_FIELD_NAME);

    ErrorCode ERR_GRAPHQL_DUPLICATE_FRAGMENT_DEF = define("nop.err.graphql.duplicate-fragment-def",
            "片段定义已存在:{fragmentName}", ARG_FRAGMENT_NAME, ARG_OLD_LOC);

    ErrorCode ERR_GRAPHQL_FIELD_NO_TYPE = define("nop.err.graphql.field-no-type",
            "对象[{objName}]中出现的字段[{fieldName}]没有定义数据类型", ARG_OBJ_NAME, ARG_FIELD_NAME);

    ErrorCode ERR_GRAPHQL_FIELD_NOT_ALLOW_SELECTION = define("nop.err.graphql.field-not-allow-selection",
            "对象[{objName}]中字段[{fieldName}]不是对象类型，不允许选择字段", ARG_OBJ_NAME, ARG_FIELD_NAME);

    ErrorCode ERR_GRAPHQL_FIELD_EXTEND_TYPE_MISMATCH = define("nop.err.graphql.field-extend-type-mismatch",
            "对象[{objName}]扩展字段[{fieldName}]时指定的类型与原类型不匹配", ARG_OBJ_NAME, ARG_FIELD_NAME);

    ErrorCode ERR_GRAPHQL_FIELD_FETCHER_ALREADY_SET = define("nop.err.graphql.field-fetcher-already-set",
            "对象[{objName}]的字段[{fieldName}]已经指定fetcher，不允许再次设置", ARG_OBJ_NAME, ARG_FIELD_NAME);

    ErrorCode ERR_GRAPHQL_METHOD_PARAM_NO_REFLECTION_NAME_ANNOTATION = define(
            "nop.err.graphql.method-param-no-reflection-name-annotation",
            "方法[{methodName}]上的参数[{argName}]没有@ReflectionName注解，也不是引擎可以识别的内部参数", ARG_METHOD_NAME, ARG_ARG_NAME);

    ErrorCode ERR_GRAPHQL_ONLY_ALLOW_ONE_CONTEXT_SOURCE_PARAM = define(
            "nop.err.graphql.only-allow-one-context-source-param", "方法[{methodName}]上最多只允许一个参数具有@ContextSource注解",
            ARG_METHOD_NAME);

    ErrorCode ERR_GRAPHQL_BATCH_LOAD_METHOD_MUST_RETURN_LIST = define(
            "nop.err.graphql.batch-load-method-must-return-list", "批量加载方法[{methodName}]必须返回列表数据类型", ARG_METHOD_NAME);

    ErrorCode ERR_GRAPHQL_FIELD_NOT_SCALAR = define("nop.err.graphql.field-not-scalar",
            "对象[{objName}]的字段[{fieldName}]不是标量数据类型", ARG_OBJ_NAME, ARG_FIELD_NAME);

    ErrorCode ERR_GRAPHQL_ACTION_WITH_EMPTY_ID_ARG = define("nop.err.graphql.action-with-empty-id-arg",
            "执行[{actionName}]操作要求id参数不能为空", ARG_ACTION_NAME);

    ErrorCode ERR_GRAPHQL_ACTION_WITH_EMPTY_IDS_ARG = define("nop.err.graphql.action-with-empty-ids-arg",
            "执行[{actionName}]操作要求id参数不能为空", ARG_ACTION_NAME);

    ErrorCode ERR_GRAPHQL_INVALID_ARG_TYPE = define("nop.err.graphql.action-invalid-arg-type",
            "执行[{actionName}]传入的参数[{argName}]的类型不正确，期待类型为:{expectedType}", ARG_ARG_NAME, ARG_ACTION_NAME,
            ARG_EXPECTED_TYPE);

    ErrorCode ERR_GRAPHQL_FIELD_FETCHER_IS_ALREADY_DEFINED = define("nop.err.graphql.field-fetcher-is-already-defined",
            "对象[{objName}]的字段[{fieldName}]存在多个加载器定义");

    ErrorCode ERR_GRAPHQL_INVALID_DIRECTIVE_LOCATION = define("nop.err.graphql.invalid-directive-location",
            "[{name}]不是合法的directive位置名称，参见GraphQLDirectiveLocation枚举类的定义", ARG_NAME);

    ErrorCode ERR_GRAPHQL_SUB_TYPE_OF_UNION_MUST_BE_OBJ_TYPE = define(
            "nop.err.graphql.sub-type-of-union-must-be-obj-type", "Union类型的子类型必须是对象类型");

    ErrorCode ERR_GRAPHQL_DUPLICATE_ACTION = define("nop.err.graphql.duplicate-action",
            "方法[{method}]和[{oldMethod}]对应的action名称重复，都是[{actionName}]", ARG_METHOD, ARG_OLD_METHOD, ARG_ACTION_NAME);

    ErrorCode ERR_GRAPHQL_METHOD_ARG_TYPE_NOT_OBJ_TYPE = define("nop.err.graphql.method-arg-type-not-obj-type",
            "函数[{methodName}]的参数[{argName}]的类型不是对象类型:{type}", ARG_METHOD_NAME, ARG_ARG_NAME, ARG_TYPE);

    ErrorCode ERR_GRAPHQL_ACTION_ARG_TYPE_NOT_OBJ_TYPE = define("nop.err.graphql.action-arg-type-not-obj-type",
            "动作[{actionName}]的参数[{argName}]的类型不是对象类型:{type}", ARG_ACTION_NAME, ARG_ARG_NAME, ARG_TYPE);

    ErrorCode ERR_GRAPHQL_NOT_OBJ_TYPE = define("nop.err.graphql.not-obj-type", "[{type}]不是对象类型，不支持字段选择", ARG_TYPE);

    ErrorCode ERR_GRAPHQL_NOT_OBJ_TYPE_FOR_FIELD = define("nop.err.graphql.not-obj-type-for-field",
            "对象[{objType}]的属性[{fieldName}]的类型[{type}]未定义", ARG_OBJ_TYPE);

    ErrorCode ERR_GRAPHQL_UNKNOWN_OBJ_TYPE = define("nop.err.graphql.unknown-obj-type", "未定义的GraphQL类型:{type}",
            ARG_TYPE);

    ErrorCode ERR_GRAPHQL_INVALID_OPERATION_NAME = define("nop.err.graphql.invalid-operation-name",
            "操作名称格式不正确:{operationName}", ARG_OPERATION_NAME);

    ErrorCode ERR_GRAPHQL_UNEXPECTED_OPERATION_TYPE = define("nop.err.graphql.unexpected-operation-type",
            "操作[{operationName}]的类型[{operationType}]不是期望类型[{expectedOperationType}]", ARG_OPERATION_NAME,
            ARG_OPERATION_TYPE, ARG_EXPECTED_OPERATION_TYPE);

    ErrorCode ERR_GRAPHQL_MULTI_CLASS_HAS_SAME_BIZ_OBJ_NAME = define(
            "nop.err.graphql.multi-class-has-same-biz-obj-name", "类[{class}]和[{otherClass}]标记了同样的对象名[{bizObjName}]",
            ARG_CLASS, ARG_OTHER_CLASS, ARG_BIZ_OBJ_NAME);

    ErrorCode ERR_GRAPHQL_UNKNOWN_OPERATION = define("nop.err.graphql.unknown-operation", "未知的请求:{operationName}",
            ARG_OPERATION_NAME);

    ErrorCode ERR_GRAPHQL_UNKNOWN_DICT_VALUE_PROP = define("nop.err.graphql.unknown-dict-value-prop",
            "对象[{objName}]的属性[{propName}]上指定的[{valueProp}]字段未定义", ARG_OBJ_NAME, ARG_PROP_NAME, ARG_VALUE_PROP_NAME);

    ErrorCode ERR_GRAPHQL_UNKNOWN_DICT = define("nop.err.graphql.unknown-dict", "没有定义字典:{dictName}", ARG_DICT_NAME);

    ErrorCode ERR_GRAPHQL_INTROSPECTION_NOT_ENABLED = define("nop.err.graphql.introspection-not-enabled",
            "没有开启GraphQL的introspection支持，不允许访问系统对象");

    ErrorCode ERR_GRAPHQL_UNKNOWN_BUILTIN_TYPE = define("nop.err.graphql.unknown-builtin-type", "不支持的系统对象类型：{typeName}",
            ARG_TYPE_NAME);

    ErrorCode ERR_GRAPHQL_UNKNOWN_TRY_METHOD = define("nop.err.graphql.unknown-try-method",
            "对象[{bizObjName}]的业务方法[{operationName}]上定义的tryMethod[{tryMethod}]不存在", ARG_OPERATION_NAME, ARG_BIZ_OBJ_NAME,
            ARG_TRY_METHOD);

    ErrorCode ERR_GRAPHQL_UNKNOWN_CANCEL_METHOD = define("nop.err.graphql.unknown-try-method",
            "对象[{bizObjName}]的业务方法[{operationName}]上定义的tryMethod[{cancelMethod}]不存在", ARG_OPERATION_NAME,
            ARG_BIZ_OBJ_NAME, ARG_CANCEL_METHOD);

    ErrorCode ERR_GRAPHQL_MULTI_BIZ_FILE_FOR_BIZ_OBJ =
            define("nop.err.graphql.multi-biz-file-for-biz-obj",
                    "存在多个biz文件对应于同一个对象[{bizObjName}]:pathA={pathA},pathB={pathB}",
                    ARG_BIZ_OBJ_NAME, ARG_PATH_A, ARG_PATH_B);

    ErrorCode ERR_GRAPHQL_MULTI_META_FILE_FOR_BIZ_OBJ =
            define("nop.err.graphql.multi-meta-file-for-biz-obj",
                    "存在多个meta文件对应于同一个对象[{bizObjName}]:pathA={pathA},pathB={pathB}",
                    ARG_BIZ_OBJ_NAME, ARG_PATH_A, ARG_PATH_B);

    ErrorCode ERR_GRAPHQL_UNKNOWN_OPERATION_ARG =
            define("nop.err.graphql.unknown-operation-arg",
                    "操作[{operationName}]没有定义参数[{argName}],允许的参数为:{allowedNames}",
                    ARG_OPERATION_NAME, ARG_ARG_NAME, ARG_ALLOWED_NAMES);

    ErrorCode ERR_GRAPHQL_ACTION_RETURN_TYPE_MUST_NOT_BE_API_RESPONSE =
            define("nop.err.graphql.action-return-type-must-not-be-api-response",
                    "NopGraphQL的服务方法[{methodName}]的返回值类型不需要用ApiResponse包装，直接返回内部结果类型即可",
                    ARG_METHOD_NAME, ARG_RETURN_TYPE, ARG_CLASS);
}
