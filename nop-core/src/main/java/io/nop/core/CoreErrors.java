/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface CoreErrors {
    String ARG_NODE = "node";
    String ARG_VAR_NAME = "varName";

    String ARG_GLOBAL_VARS = "globalVars";
    String ARG_SRC_NAME = "srcName";
    String ARG_PARENTS = "parents";
    String ARG_RESOURCE = "resource";
    String ARG_TPL = "tpl";

    String ARG_OBJ = "obj";
    String ARG_PROP = "prop";
    String ARG_RESOURCE_PATH = "resourcePath";
    String ARG_DEP_STACK = "depStack";
    String ARG_NAME = "name";
    String ARG_CACHE_NAME = "cacheName";
    String ARG_ROOT_PATH = "rootPath";

    String ARG_JSON_PATH = "jsonPath";

    String ARG_PATH_PATTERN = "pathPattern";

    String ARG_RESOURCE1 = "resource1";
    String ARG_RESOURCE2 = "resource2";

    String ARG_FILE_TYPE = "fileType";
    String ARG_MODEL_TYPE = "modelType";
    String ARG_MODEL_TYPE2 = "modelType2";

    String ARG_FROM_MODEL_TYPE = "fromModelType";
    String ARG_TO_MODEL_TYPE = "toModelType";

    String ARG_TRANSFORM = "transform";

    String ARG_COMPONENT_PATH = "componentPath";

    String ARG_SRC = "src";
    String ARG_DEST = "dest";

    String ARG_FILE_RANGE = "fileRange";
    String ARG_LENGTH = "length";
    String ARG_POS = "pos";
    String ARG_LOC = "loc";

    String ARG_PROLOG = "prolog";
    String ARG_TAG_NAME = "tagName";
    String ARG_CHILD = "child";
    String ARG_INDEX = "index";
    String ARG_ATTR_NAME = "attrName";
    String ARG_ATTR_VALUE = "attrValue";

    String ARG_CLASS_NAME = "className";

    String ARG_MODEL = "model";

    String ARG_METHOD_NAME = "methodName";
    String ARG_COUNT = "count";
    String ARG_EXPECTED_COUNT = "expectedCount";

    String ARG_ALLOWED_METHODS = "allowedMethods";

    String ARG_OLD_VALUE = "oldValue";
    String ARG_NEW_VALUE = "newValue";

    String ARG_DIR = "dir";
    String ARG_TARGET_FILE = "targetFile";

    String ARG_CURRENT_PATH = "currentPath";

    String ARG_NAMESPACE = "namespace";

    String ARG_READ_COUNT = "readCount";

    String ARG_OP = "op";

    String ARG_ROW = "row";
    String ART_TABLE = "table";
    String ARG_ROW_INDEX = "rowIndex";
    String ARG_COL_INDEX = "colIndex";
    String ARG_CELL = "cell";
    String ARG_MERGE_DOWN = "mergeDown";
    String ARG_MERGE_ACROSS = "mergeAcross";

    String ARG_MODULE_ID = "moduleId";
    String ARG_MODULE_NAME = "moduleName";

    String ARG_KEY = "key";

    String ARG_PARENT_TYPE = "parentType";

    String ARG_MAX_LEVEL = "maxLevel";

    String ARG_PROP_NAME = "propName";
    String ARG_BEAN = "bean";
    String ARG_PROP_NAMES = "propNames";

    String ARG_SERIALIZER = "serializer";
    String ARG_DESERIALIZER = "deserializer";

    String ARG_TYPE_NAME = "typeName";

    String ARG_PROP_PATH = "propPath";
    String ARG_TYPE_VALUE = "typeValue";

    String ARG_ALIAS = "alias";
    String ARG_OTHER_PROP_NAME = "otherPropName";

    String ARG_AST_NODE = "astNode";
    String ARG_PARENT_NODE = "parentNode";
    String ARG_OLD_PARENT_NODE = "oldParentNode";

    String ARG_IDENTIFIER = "identifier";

    String ARG_START_LOC = "startLoc";
    String ARG_OLD_LOC = "oldLoc";

    String ARG_MESSAGE = "message";

    String ARG_TYPE = "type";
    String ARG_TEXT = "text";
    String ARG_VALUE_PATH = "valuePath";

    String ARG_SRC_TYPE = "srcType";
    String ARG_TARGET_TYPE = "targetType";
    String ARG_VALUE = "value";
    String ARG_PATTERN = "pattern";

    String ARG_LOC_A = "locA";
    String ARG_LOC_B = "locB";
    String ARG_VALUE_A = "valueA";
    String ARG_VALUE_B = "valueB";

    String ARG_VERTEX = "vertex";

    String ARG_FILE_NAME = "fileName";
    String ARG_TITLE = "title";

    String ARG_EXCEPTION = "exception";
    String ARG_ERROR_CODE = "errorCode";
    String ARG_EXPECTED = "expected";

    String ARG_SIZE = "size";

    String ARG_MIN_ITEMS = "minItems";
    String ARG_MAX_ITEMS = "maxItems";

    String ARG_SERVICE_NAME = "serviceName";
    String ARG_SERVICE_METHOD = "serviceMethod";

    String ARG_EXPR = "expr";

    String ARG_DICT_NAME = "dictName";

    String ARG_SRC_LENGTH = "srcLength";
    String ARG_TARGET_LENGTH = "targetLength";

    String ARG_STD_PATH = "stdPath";
    String ARG_PATH = "path";

    String ARG_BASE_PATH = "basePath";
    String ARG_OTHER_PATH = "otherPath";

    String ARG_CELL_POS = "cellPos";
    String ARG_IMP_FIELD_NAME = "impFieldName";
    String ARG_IMP_FIELD_DISPLAY_NAME = "impFieldDisplayName";

    String ARG_PARENT = "parent";

    String ARG_ALLOWED_NS = "allowedNs";
    String ARG_XML_NAME = "xmlName";

    String ARG_NODE_ID = "nodeId";

    String ARG_NODE_NAMES = "nodeNames";

    ErrorCode ERR_JSON_VALUE_NOT_NODE =
            define("nop.err.core.json-value-not-node", "值无法转换为XNode类型");

    ErrorCode ERR_JSON_FLATTEN_KEY_CONFLICT = define("nop.err.core.json.flatten-key-conflict",
            "将JSON按key嵌套展平时出现重名的key:[{key}]", ARG_KEY);

    ErrorCode ERR_JSON_EVAL_BIND_EXPR_FAIL = define("nop.err.core.json.eval-bind-expr-fail",
            "执行[{key}]对应的JSON数据绑定表达式[{expr}]失败", ARG_EXPR, ARG_KEY);

    ErrorCode ERR_JSON_BIND_OPTIONS_NOT_STRING = define("nop.err.core.json.bind-options-not-string",
            "类型[{type}]的绑定表达式的参数必须为字符串", ARG_TYPE);

    ErrorCode ERR_JSON_BIND_OPTIONS_NOT_VALID_VPATH = define("nop.err.core.json.bind-options-not-valid-vpath",
            "类型[{type}]的绑定表达式的参数必须为虚拟文件路径", ARG_TYPE);

    ErrorCode ERR_JSON_BIND_EXPR_INVALID_TYPE = define("nop.err.core.json.bind-expr-invalid-type", "未识别的绑定表达式类型：{type}",
            ARG_TYPE);

    ErrorCode ERR_JSON_LIST_IS_READONLY = define("nop.err.core.json.list-is-readonly", "只读列表不允许修改");

    ErrorCode ERR_JSON_MAP_IS_READONLY = define("nop.err.core.json.map-is-readonly", "只读容器不允许修改");

    ErrorCode ERR_XNODE_IS_READONLY = define("nop.err.core.xml.node-is-readonly", "试图修改只读节点", "node");

    ErrorCode ERR_JSON_WRITER_IS_CLOSED = define("nop.err.core.json.json-writer-is-closed", "JsonWriter已关闭");

    ErrorCode ERR_HANDLER_STATE_INCOMPLETE = define("nop.err.core.lang.handler-state-incomplete", "处理状态不正确，非正常结束");

    ErrorCode ERR_HANDLER_STATE_ALREADY_COMPLETED = define("nop.err.core.lang.handler-state-already-completed",
            "处理器已进入结束状态，不能再继续处理");

    ErrorCode ERR_JSON_VALUE_NOT_MATCH = define("nop.err.core.json.value-not-match",
            "字段[{valuePath}]的值[{value}]不是期待的值[{expected}]", ARG_VALUE_PATH, ARG_VALUE, ARG_EXPECTED);

    ErrorCode ERR_JSON_MATCH_NO_VALUE = define("nop.err.core.json.match-no-value",
            "缺少字段[{valuePath}]的值，格式要求为[{pattern}]", ARG_VALUE_PATH, ARG_PATTERN);

    ErrorCode ERR_JSON_MATCH_NO_PATTERN = define("nop.err.core.json.match-no-pattern",
            "JSON数据中存在多余的字段[{valuePath}]，它的值为[{value}]", ARG_VALUE_PATH, ARG_VALUE);

    ErrorCode ERR_JSON_VALUE_SIZE_NOT_MATCH = define("nop.err.core.json.value-size-not-match",
            "字段[{valuePath}]对应的集合大小[{size}]不是期待的值[{expected}]", ARG_VALUE_PATH, ARG_SIZE, ARG_EXPECTED);

    ErrorCode ERR_JSON_VALUE_PATTERN_NOT_MATCH = define("nop.err.core.json.value-pattern-not-match",
            "字段[{valuePath}]的值[{value}]不匹配模式[{expected}]", ARG_VALUE_PATH, ARG_VALUE, ARG_PATTERN);

    ErrorCode ERR_JSON_VALUE_NOT_SERIALIZABLE = define("nop.err.core.json.value-not-serializable",
            "JSON序列化失败，遇到不可序列化的值");

    ErrorCode ERR_LOOP_VAR_IS_ALREADY_DEFINED = define("nop.err.core.loop.var-is-already-define",
            "变量[{varName}]的定义已存在，不能被重复定义", ARG_VAR_NAME);

    ErrorCode ERR_LOOP_SRC_VAR_NOT_DEFINED = define("nop.err.core.loop.src-var-not-defined", "来源变量[{srcName}]尚未被定义",
            ARG_SRC_NAME);

    ErrorCode ERR_LOOP_SAME_VAR_AND_SRC = define("nop.err.core.loop.same-var-and-src",
            "循环变量名[{varName}]和来源变量[{srcName}]名不能相同", ARG_VAR_NAME, ARG_SRC_NAME);

    ErrorCode ERR_LOOP_VAR_NOT_DEFINED = define("nop.err.core.loop.var-not-defined", "循环变量[{varName}]没有在NestedLoop中定义",
            ARG_VAR_NAME);

    ErrorCode ERR_TREE_ILLEGAL_VISIT_STATE = define("nop.err.core.tree.illegal-visit-state", "遍历树形结构时状态不正确",
            ARG_PARENTS);

    ErrorCode ERR_TREE_TABLE_UNSUPPORTED_CHILD_POS = define("nop.err.core.tree.table.unsupported-child-pos",
            "不支持的子节点位置", ARG_POS);

    ErrorCode ERR_TPL_OUTPUT_TO_RESOURCE_FAIL = define("nop.err.core.tpl.output-to-resource-fail", "生成到文件时失败",
            ARG_RESOURCE, ARG_TPL);

    ErrorCode ERR_TPL_OUTPUT_TEXT_FAIL = define("nop.err.core.tpl.output-text-fail", "生成文本时失败", ARG_TPL);

    ErrorCode ERR_TPL_OUTPUT_BYTES_FAIL = define("nop.err.core.tpl.output-bytes-fail", "生成模板输出时失败", ARG_TPL);

    ErrorCode ERR_EVAL_DISABLED_EVAL_SCOPE = define("nop.err.core.eval.disabled-eval-scope", "scope已被禁用，不允许访问此方法");

    ErrorCode ERR_EVAL_UNKNOWN_GLOBAL_VAR = define("nop.err.core.eval.unknown-global-var", "未定义的全局变量: {name}");

    ErrorCode ERR_EVAL_FRAME_NOT_SUPPORTED_OPERATION = define("nop.err.core.eval.frame.not-supported-operation",
            "不支持此操作");

    ErrorCode ERR_EVAL_OUTPUT_TEXT_FAIL = define("nop.err.core.eval.output-text-fail", "输出文本失败");

    ErrorCode ERR_EVAL_FUNCTION_NAME_MUST_STARTS_WITH_LOWER_CASE = define(
            "nop.err.core.eval.function-name-must-starts-with-lower-case", "函数名的首字母必须小写", ARG_NAME);

    ErrorCode ERR_LANG_AST_NODE_NOT_ALLOW_MULTIPLE_PARENT = define(
            "nop.err.core.lang.ast-node-not-allow-multiple-parent", "AST语法节点不允许存在多个父节点", ARG_AST_NODE, ARG_PARENT_NODE);

    ErrorCode ERR_LANG_AST_NODE_NOT_ALLOW_PARENT = define("nop.err.core.lang.ast-node-not-allow-parent",
            "AST语法节点不应该具有父节点", ARG_AST_NODE);

    ErrorCode ERR_LANG_AST_NODE_PROP_NOT_ALLOW_EMPTY = define("nop.err.core.lang.ast-node-prop-not-allow-empty",
            "AST语法节点[{astNode}]的属性[{propName}]不允许为空", ARG_AST_NODE, ARG_PROP_NAME);

    ErrorCode ERR_LANG_AST_NODE_INVALID_IDENTIFIER = define("nop.err.core.lang.ast-node-invalid-identifier",
            "[{identifier}]不是合法的变量标识", ARG_AST_NODE, ARG_IDENTIFIER);

    ErrorCode ERR_LANG_AST_NODE_PROP_NO_ENOUGH_ITEMS = define("nop.err.core.lang.ast-node-prop-no-enough-items",
            "AST语法节点[{astNode}]的属性[{propName}]最小个数必须大于{minItems}", ARG_AST_NODE, ARG_PROP_NAME, ARG_MIN_ITEMS);

    ErrorCode ERR_LANG_AST_NODE_PROP_TOO_MANY_ITEMS = define("nop.err.core.lang.ast-node-prop-too-many-items",
            "AST语法节点[{astNode}]的属性[{propName}]最大个数超过限制:{maxItems}", ARG_AST_NODE, ARG_PROP_NAME, ARG_MAX_ITEMS);

    ErrorCode ERR_CONTEXT_BROKEN_CONTEXT_STACK = define("nop.err.core.context.broken-context-stack",
            "leaveContext和enterContext必须配对调用");

    ErrorCode ERR_REFLECT_INVALID_EXT_PROP_NAME =
            define("nop.err.core.reflect.invalid-ext-prop-name",
                    "扩展属性名[{name}]格式不正确。属性名不允许含有字符.，字符.被保留为复杂属性的分隔符，例如a.b.c", ARG_NAME);

    ErrorCode ERR_REFLECT_UNKNOWN_PROP = define("nop.err.core.reflect.unknown-prop", "对象[{obj}]没有属性[{prop}]", ARG_OBJ,
            ARG_PROP);

    ErrorCode ERR_REFLECT_UNRESOLVED_TYPE = define("nop.err.core.reflect.unresolved-type", "未定义的类型：{typeName}",
            ARG_TYPE_NAME);

    ErrorCode ERR_REFLECT_NOT_SUPPORT_ARRAY_CLASS_MODEL = define("nop.err.core.reflect.not-support-array-class-model",
            "不支持获取数组类型的ClassModel", ARG_CLASS_NAME);

    ErrorCode ERR_REFLECT_UNKNOWN_BEAN_CLASS = define("nop.err.core.reflect.unknown-bean-class",
            "未知的bean类型：{className}", ARG_CLASS_NAME);

    ErrorCode ERR_REFLECT_NOT_COLLECTION_TYPE = define("nop.err.core.reflect.not-collection-type",
            "不是集合类型或者数组类型:{className}", ARG_CLASS_NAME);

    ErrorCode ERR_REFLECT_CAST_VALUE_TO_TARGET_TYPE_FAIL = define("nop.err.core.reflect.cast-value-not-target-type",
            "将类型为[{srcType}]的值转换为目标类型[{targetType}]失败", ARG_SRC_TYPE, ARG_TARGET_TYPE);

    ErrorCode ERR_LANG_DYNAMIC_OBJECT_UNKNOWN_METHOD = define("nop.err.core.dynamic.object-unknown-method",
            "动态对象不支持方法:{methodName}", ARG_METHOD_NAME);

    ErrorCode ERR_LANG_DYNAMIC_OBJECT_UNKNOWN_PROP = define("nop.err.core.dynamic.object-unknown-prop",
            "动态对象不支持属性:{propName}", ARG_PROP_NAME);

    ErrorCode ERR_LANG_DYNAMIC_OBJECT_DUPLICATE_PROP = define("nop.err.core.dynamic.object-duplicate-prop",
            "动态对象的属性不允许重名：propName={},oldValue={},newValue={}", ARG_PROP_NAME, ARG_OLD_VALUE, ARG_NEW_VALUE);

    ErrorCode ERR_LANG_DYNAMIC_OBJECT_EMPTY_PROP_NAME = define("nop.err.core.dynamic.object-empty-prop-name",
            "动态对象的属性名不能为空");

    ErrorCode ERR_TYPE_TYPE_STRING_NOT_END_PROPERLY = define("nop.err.core.type.type-string-not-end-properly",
            "类型定义没有正确结束");

    ErrorCode ERR_REFLECT_TYPE_INVALID_ARG = define("nop.err.core.reflect.type-invalid-arg", "泛型类型的参数不匹配",
            ARG_TYPE_NAME);

    ErrorCode ERR_COMPONENT_DEP_STACK_CONTAINS_LOOP = define("nop.err.core.component.dep-stack-contains-loop",
            "组件之间不允许存在循环依赖", ARG_DEP_STACK);

    ErrorCode ERR_COMPONENT_PARSE_MISSING_RESOURCE = define("nop.err.core.component.parse-missing-resource",
            "待解析的资源文件不存在:{resourcePath}", ARG_RESOURCE_PATH, ARG_RESOURCE);

    ErrorCode ERR_COMPONENT_RESOURCE_CACHE_RETURN_NULL = define("nop.err.core.component.resource-cache-return-null",
            "装载资源文件返回结果为空:{resourcePath}", ARG_RESOURCE_PATH, ARG_RESOURCE);

    ErrorCode ERR_COMPONENT_CACHE_DUPLICATE_REGISTRATION = define("nop.err.core.component.cache.duplicate-registration",
            "注册cache失败，同名的cache已存在:{cacheName}", ARG_CACHE_NAME);

    ErrorCode ERR_COMPONENT_UNKNOWN_MODEL_TYPE = define("nop.err.core.component.unknown-model-type",
            "未知的模型类型:{modelType}", ARG_MODEL_TYPE);

    ErrorCode ERR_COMPONENT_UNKNOWN_MODEL_FILE_TYPE = define("nop.err.core.component.unknown-model-file-type",
            "未知的模型文件类型:{fileType}", ARG_FILE_TYPE);

    ErrorCode ERR_COMPONENT_UNKNOWN_COMPONENT_FILE_TYPE = define("nop.err.core.component.unknown-component-file-type",
            "未知的组件文件类型:{fileType}", ARG_FILE_TYPE, ARG_COMPONENT_PATH);

    ErrorCode ERR_COMPONENT_UNDEFINED_COMPONENT_MODEL_TRANSFORM = define(
            "nop.err.core.component.undefined-component-model-transform",
            "组件模型[{modelType}]没有定义转换类型[{transform}]，无法转换到指定类型", ARG_MODEL_TYPE, ARG_TRANSFORM);

    ErrorCode ERR_COMPONENT_INVALID_MODEL_PATH =
            define("nop.err.core.component.invalid-model-path",
                    "模型路径格式不合法:{resourcePath}", ARG_RESOURCE_PATH);

    ErrorCode ERR_COMPONENT_UNKNOWN_FILE_TYPE_FOR_MODEL_TYPE = define(
            "nop.err.core.component.unknown-file-type-for-model-type", "模型[{modelType}]不支持文件类型[{fileType}]",
            ARG_FILE_TYPE, ARG_MODEL_TYPE);

    ErrorCode ERR_COMPONENT_INVALID_FILE_TYPE = define("nop.err.core.component.invalid-file-type",
            "模型[{modelType}]的文件类型[{fileType}]格式不合法，只允许fileExt或者xxx.fileExt这两种形式", ARG_FILE_TYPE, ARG_MODEL_TYPE);

    ErrorCode ERR_COMPONENT_MODEL_FILE_TYPE_CONFLICT = define("nop.err.core.component.model-file-type-conflict",
            "类型为[{modelType}]和[{modelType2}]的模型具有同样的文件类型[{fileType}]", ARG_MODEL_TYPE, ARG_FILE_TYPE, ARG_MODEL_TYPE2);

    ErrorCode ERR_COMPONENT_MODEL_TRANSFORMER_ALREADY_EXISTS = define(
            "nop.err.core.component.model-transformer-already-exists", "从类型[fromModelType]到类型[toModelType]的转换器已经存在",
            ARG_FROM_MODEL_TYPE, ARG_TO_MODEL_TYPE);

    ErrorCode ERR_COMPONENT_NOT_COMPOSITE_COMPONENT = define("nop.err.core.component.not-composite-component",
            "对象不是复合组件类型，不支持通过sub参数来获取子组件", ARG_COMPONENT_PATH);

    ErrorCode ERR_COMPONENT_NOT_ALLOW_CHANGE = define("nop.err.core.component.not-allow-change",
            "模型对象已经被冻结，不允许被修改:{resourcePath}", ARG_RESOURCE_PATH);

    ErrorCode ERR_COMPONENT_NO_GEN_PATH_STRATEGY = define("nop.err.core.component.no-exp-path-strategy",
            "类型为[{modelType}]的模型没有指定组件路径生成策略", ARG_MODEL_TYPE);

    ErrorCode ERR_COMPONENT_NO_COMPONENT_GENERATOR = define("nop.err.core.component.no-component-generator",
            "类型为[{modelType}]的模型没有指定组件生成器", ARG_MODEL_TYPE);

    ErrorCode ERR_RESOURCE_VIRTUAL_FILE_SYSTEM_NOT_INITIALIZED = define(
            "nop.err.core.resource.virtual-file-system-not-initialized", "虚拟文件系统尚未初始化");

    ErrorCode ERR_RESOURCE_DIR_NOT_SUPPORT_STREAM = define("nop.err.core.resource.dir-not-support-stream",
            "目录对象不支持数据流操作:{resource}", ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_SAVE_TO_RESOURCE_FAIL = define("nop.err.core.resource.save-to-resource-fail",
            "保存资源文件[{src}]到目标[{dest}]失败", ARG_SRC, ARG_DEST);

    ErrorCode ERR_RESOURCE_WRITE_TO_STREAM_FAIL = define("nop.err.core.resource.write-to-stream-fail",
            "保存资源文件[{src}]到数据流中失败", ARG_SRC);

    ErrorCode ERR_RESOURCE_SAVE_FROM_STREAM_FAIL = define("nop.err.core.resource.save-from-stream-fail",
            "保存数据流到资源文件[{resourcePath}]失败", ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_NOT_EXISTS = define("nop.err.core.resource.not-exists",
            "资源文件[{resourcePath}]不存在", ARG_RESOURCE, ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_GET_INPUT_STREAM_FAIL = define("nop.err.core.resource.get-input-stream-fail",
            "打开资源文件[{resource}]的输入流失败", ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_GET_OUTPUT_STREAM_FAIL = define("nop.err.core.resource.get-output-stream-fail",
            "打开资源文件[{resource}]的输出流失败", ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_NOT_CONTAINS_FILE_RANGE = define("nop.err.core.resource.not-contains-file-range",
            "文件[{resource}]长度为{length}, 无法定位文件区间：[{range}]", ARG_LENGTH, ARG_FILE_RANGE, ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_NO_LENGTH = define("nop.err.core.resource.no-length", "文件没有长度属性:{resource}", ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_STREAM_SKIP_TO_POS_FAILED = define("nop.err.core.resource.stream-skip-to-pos-failed",
            "跳到文件指定位置[pos]处失败:{resource}", ARG_RESOURCE, ARG_POS);

    ErrorCode ERR_RESOURCE_UNKNOWN_RESOURCE_NOT_ALLOW_OPERATION = define(
            "nop.err.core.resource.unknown-resource-not-allow-operation", "未知资源对象[{resourcePath}]不支持此操作",
            ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_CREATE_TEMP_FILE_FAIL = define("nop.err.core.resource.create-temp-file-fail",
            "在目录{resource}下创建临时文件失败", ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_CREATE_NEW_FILE_FAIL = define("nop.err.core.resource.create-new-file-fail",
            "创建新文件失败：{resource}", ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_OPEN_INPUT_STREAM_FAIL = define("nop.err.core.resource.open-input-stream-fail",
            "打开文件输入流失败：{resource}", ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_OPEN_OUTPUT_STREAM_FAIL = define("nop.err.core.resource.open-output-stream-fail",
            "打开文件输出流失败：{resource}", ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_INVALID_PATH_FOR_CLASSPATH_RESOURCE = define(
            "nop.err.core.resource.invalid-path-for-classpath-resource", "非法的classpath资源路径", ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_IS_READONLY_FILE = define("nop.err.core.resource.is-readonly-file", "只读文件不支持写操作",
            ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_DIR_NOT_SUPPORT_READ_WRITE = define("nop.err.core.resource.dir-not-support-read-write",
            "目录资源不支持读写操作", ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_NO_CURRENT_PATH = define("nop.err.core.resource.no-current-path", "上下文环境中没有设置currentPath");

    ErrorCode ERR_RESOURCE_EXCEED_MAX_DEPS_STACK_SIZE = define("nop.err.core.resource.exceed-max-deps-stack-size",
            "文件可能存在循环依赖：path={resourcePath},root={rootPath}", ARG_RESOURCE_PATH, ARG_ROOT_PATH);

    ErrorCode ERR_RESOURCE_CURRENT_PATH_CONTAINS_INVALID_DELTA_LAYER_ID = define(
            "nop.err.core.resource.current-path-contains-invalid-delta-layer-id",
            "当前路径[{currentPath}]的deltaLayerId与系统当前配置不符", ARG_CURRENT_PATH);

    ErrorCode ERR_RESOURCE_INVALID_DELTA_LAYER_ID = define("nop.err.core.resource.invalid-delta-id",
            "路径[{resourcePath}]的deltaLayerId与系统当前配置不符", ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_NOT_ALLOW_ACCESS_INTERNAL_PATH = define(
            "nop.err.core.resource.now-allow-access-internal-path", "不允许直接访问内部路径:{resourcePath}", ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_NOT_DIR = define("nop.err.core.resource.not-dir", "资源对象[{resource}]对应的不是目录", ARG_RESOURCE);

    ErrorCode ERR_RESOURCE_STORE_PATH_NOT_SUPPORTED = define("nop.err.core.resource.store.path-not-supported",
            "不支持的资源存储路径:{resourcePath}", ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_ZIP_DIR_TO_FILE_FAIL = define("nop.err.core.resource.store.zip-to-dir-fail",
            "压缩目录[{dir}]到文件[{targetFile}]失败", ARG_DIR, ARG_TARGET_FILE);

    ErrorCode ERR_RESOURCE_UNZIP_TO_DIR_FAIL = define("nop.err.core.resource.zip.unzip-to-dir-fail", "解压缩到目录[{dir}]失败",
            ARG_DIR);

    ErrorCode ERR_RESOURCE_INVALID_ZIP_ENTRY_NAME = define("nop.err.core.resource.invalid-zip-entry-name",
            "非法的ZipEntry名称:{name}", ARG_NAME);

    ErrorCode ERR_RESOURCE_INVALID_DIR_ZIP_ENTRY_NAME = define("nop.err.core.resource.invalid-zip-entry-name",
            "ZipEntry目录名必须以/结尾:{name}", ARG_NAME);

    ErrorCode ERR_RESOURCE_DIR_PATH_SHOULD_END_WITH_SLASH = define(
            "nop.err.core.resource.dir-path-should-end-with-slash", "目录对象的路径必须以/结尾:{resourcePath}", ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_IN_MEMORY_STORE_NOT_ALLOW_SAVE = define(
            "nop.err.core.resource.in-memory-store-not-allow-save", "内存文件存储不支持保存操作", ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_INVALID_FILE_PATH_PATTERN = define("nop.err.core.resource.invalid-file-path-pattern",
            "文件路径匹配模式中只允许*和文件名，不支持其他符号", ARG_PATH_PATTERN);

    ErrorCode ERR_RESOURCE_UNKNOWN_NAMESPACE = define("nop.err.core.resource.unknown-namespace",
            "资源文件[{resourcePath}]包含未知的名字空间：{namespace}", ARG_RESOURCE_PATH, ARG_NAMESPACE);

    ErrorCode ERR_RESOURCE_READ_CSV_ROW_FAIL = define("nop.err.core.resource.read-csv-row-fail",
            "读取CSV文件[{resourcePath}]的第{readCount}行时失败", ARG_RESOURCE_PATH, ARG_READ_COUNT);

    ErrorCode ERR_RESOURCE_INVALID_PATH = define("nop.err.core.resource.invalid-path",
            "资源路径只能使用/为路径分隔符，且不能以/结尾，不能包含../等相对路径，也不能包含windows路径所不允许的特殊字符:{resourcePath}", ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_INVALID_RELATIVE_NAME = define("nop.err.core.resource.invalid-relative-name",
            "相对路径不能以/开始，只能使用/为路径分隔符，不能包含../等相对路径，也不能包含windows路径所不允许的特殊字符:{resourcePath}", ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_PATH_NOT_IN_NAMESPACE = define("nop.err.core.resource.path-not-in-namespace",
            "资源路径[{resourcePath}]不属于名字空间[{namespace}]", ARG_RESOURCE_PATH, ARG_NAMESPACE);

    ErrorCode ERR_RESOURCE_NOT_ALLOW_ACCESS_PATH = define("nop.err.core.resource.not-allow-path",
            "不允许访问资源路径[{resourcePath}]", ARG_RESOURCE_PATH);

    ErrorCode ERR_RESOURCE_INVALID_MODULE_ID = define("nop.err.core.resource.invalid-module-id",
            "模块ID必须是A/B这种格式，而现在是：{moduleId}",
            ARG_MODULE_ID);

    ErrorCode ERR_RESOURCE_INVALID_MODULE_NAME = define("nop.err.core.resource.invalid-module-name",
            "模块名必须是A-B这种格式，而现在是:{moduleName}", ARG_MODULE_NAME);

    ErrorCode ERR_RESOURCE_MODULE_PATH_RESOLVE_TO_MULTI_FILE = define(
            "nop.err.core.resource.module-path-resolve-to-multi-file", "虚拟文件路径[{stdPath}]对应到多个模块文件:{path},{otherPath}",
            ARG_STD_PATH, ARG_PATH, ARG_OTHER_PATH);

    ErrorCode ERR_RESOURCE_DUPLICATE_VFS_RESOURCE = define("nop.err.core.resource.duplicate-vfs-resource",
            "多个jar包中包含同样的虚拟文件路径[{path}]", ARG_PATH, ARG_RESOURCE1, ARG_RESOURCE2);

    ErrorCode ERR_RESOURCE_INVALID_VERSIONED_PATH = define("nop.err.core.resource.invalid-versioned-path",
            "资源路径格式不满足要求：{path}", ARG_PATH, ARG_BASE_PATH);

    ErrorCode ERR_RESOURCE_VERSIONED_PATH_NO_VERSION = define("nop.err.core.resource.versiond-path-no-version",
            "多版本资源路径的文件名没有包含版本号信息", ARG_PATH);
    ErrorCode ERR_XML_NOT_NODE_VALUE = define("nop.err.core.xml.not-node-value",
            "值不是XNode类型:{value}", ARG_VALUE);
    ErrorCode ERR_XML_EXCEED_MAX_NESTED_LEVEL = define("nop.err.core.xml.exceed-max-nested-level", "XML嵌套层次超过限制");

    ErrorCode ERR_XML_PARSE_RESOURCE_FAIL = define("nop.err.core.xml.parse-resource-fail", "解析XML文件失败");

    ErrorCode ERR_XML_INVALID_XML_PROLOG = define("nop.err.core.xml.invalid-xml-prolog", "XML文件头格式不正确");

    ErrorCode ERR_XML_INVALID_INSTRUCTION = define("nop.err.core.xml.invalid-instruction", "XML指令格式不合法");

    ErrorCode ERR_XML_HANDLER_BEGIN_END_MISMATCH = define("nop.err.core.xml.handler-begin-end-mismatch",
            "beginNode和endNode必须配对调用");

    ErrorCode ERR_XML_NOT_ALLOW_EMPTY_PROP_NAME = define("nop.err.core.xml.not-allow-empty-prop-name",
            "访问XNode节点的属性时属性名不能为空", ARG_NODE);

    ErrorCode ERR_XML_NOT_ALLOW_MULTIPLE_ROOT = define("nop.err.core.xml.not-allow-multiple-root", "XML文件不允许存在多个根节点");

    ErrorCode ERR_XML_UNEXPECTED_EOF = define("nop.err.core.xml.unexpected-eof", "解析XML结构不完整");

    ErrorCode ERR_XML_UNKNOWN_XML_ENTITY = define("nop.err.core.xml.unknown-xml-entity", "未知的XML entity");

    ErrorCode ERR_XML_PARSE_XML_ENTITY_FAIL = define("nop.err.core.xml.parse-xml-entity-fail", "解析XML entity失败");

    ErrorCode ERR_XML_PARSE_ESCAPE_CHAR_FAIL = define("nop.err.core.xml.parse-escape-char-fail", "解析XML转义字符失败");

    ErrorCode ERR_XML_UNEXPECTED_CHAR = define("nop.err.core.xml.unexpected-char", "解析XML失败，下一个字符不是期待的字符");

    ErrorCode ERR_XML_TAG_NOT_END_PROPERLY = define("nop.err.core.xml.tag-end-not-match-tag-start",
            "XML标签没有正常关闭，期待的字符是{expected}", ARG_EXPECTED);

    ErrorCode ERR_XML_IS_EMPTY = define("nop.err.core.xml.is-empty", "XML文本内容为空");

    ErrorCode ERR_XML_MULTIPLE_CHILD_WITH_SAME_TAG_NAME = define("nop.err.core.xml.multiple-child-with-same-tag-name",
            "XML节点[{node}]的多个子节点具有同样的标签名[{tagName}]", ARG_TAG_NAME, ARG_NODE);

    ErrorCode ERR_XML_ATTACH_CHILD_NOT_ALLOW_NULL = define("nop.err.core.xml.attach-child-not-allow-null",
            "追加的XML子节点不允许为null");

    ErrorCode ERR_XML_ATTACH_CHILD_SHOULD_NOT_HAS_PARENT = define("nop.err.core.xml.attach-child-should-not-has-parent",
            "追加的XML子节点的父节点必须为null, 一个节点只能有一个父节点");
    ErrorCode ERR_XML_INVALID_CHILD_INDEX = define("nop.err.core.xml.invalid-child-index", "子节点下标[{index}]超出区间",
            ARG_NODE, ARG_INDEX);

    ErrorCode ERR_XML_DUPLICATE_ATTR_NAME = define("nop.err.core.xml.duplicate-attr-name",
            "XML节点的属性名重复：attrName={attrName}", ARG_ATTR_NAME, ARG_NODE);

    ErrorCode ERR_XML_ATTR_VALUE_NOT_QUOTED = define("nop.err.core.xml.attr-value-not-quoted", "XML属性必须以引号包裹");

    ErrorCode ERR_XML_STRING_NOT_END_PROPERLY = define("nop.err.core.xml.string-not-end-properly", "字符串没有正常结束");

    ErrorCode ERR_XML_DOC_NOT_END_PROPERLY = define("nop.err.core.xml.doc-not-end-properly", "XML文档没有正常结束");

    ErrorCode ERR_XML_INVALID_NODE_JSON_TYPE = define("nop.err.core.xml.invalid-node-json-type",
            "[{attrName}]的属性值[{attrValue}]不合法，可选值为map, list, value", ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XML_TRANSFORM_TO_JSON_FAIL = define("nop.err.core.xml.transform-to-json-fail", "将XNode转换为json格式失败",
            ARG_NODE);

    ErrorCode ERR_XML_NOT_ALLOW_BOTH_CONTENT_AND_CHILD = define("nop.err.core.xml.not-allow-both-content-and-child",
            "XNode不能同时具有content值和子节点", ARG_NODE);

    ErrorCode ERR_XML_ATTR_IS_EMPTY = define("nop.err.core.xml.attr-is-empty", "XNode属性[{attrName}]不能为空",
            ARG_ATTR_NAME);

    // ErrorCode ERR_XML_ATTR_CAST_TYPE_FAIL =
    // define("nop.err.core.xml.attr-cast-type-fail","试图将XNode属性[{attrName}]转换为类型[{targetType}]失败，属性值:[{value}]",
    // ARG_ATTR_NAME,ARG_TARGET_TYPE, ARG_VALUE);

    ErrorCode ERR_XML_NO_XPATH_PROVIDER = define("nop.err.core.xml.no-xpath-provider", "没有注册XPath解析器");

    ErrorCode ERR_JSON_TRANSFORM_TO_XML_FAIL = define("nop.err.core.json.transform-to-xml-fail", "将JSON对象转换为XNode格式失败");

    ErrorCode ERR_JSON_STRING_NOT_END_PROPERLY = define("nop.err.core.json.string-not-end-properly", "字符串没有正常结束");

    ErrorCode ERR_JSON_DOC_NOT_END_PROPERLY = define("nop.err.core.json.doc-not-end-properly", "JSON文档没有正常结束");

    ErrorCode ERR_JSON_UNEXPECTED_GEN_EXTENDS_RESULT_TYPE = define(
            "nop.err.core.json.unexpected-exp-extends-result-type",
            "x:exp-extends生成的数据类型必须是List<Map>或者Map，而当前结果类型是[{className}]", ARG_CLASS_NAME);

    ErrorCode ERR_HANDLER_EXCEED_MAX_NESTED_LEVEL = define("nop.err.core.lang.handler-exceed-max-nested-level",
            "嵌套层次超过限制:maxLevel={maxLevel}", ARG_MAX_LEVEL);

    ErrorCode ERR_JSON_STRICT_MODEL_KEY_NOT_DOUBLE_QUOTED = define(
            "nop.err.core.json.strict-mode.key-not-double-quoted", "标准JSON语法要求Map的key必须用双引号包裹");

    ErrorCode ERR_JSON_STRICT_MODEL_STRING_NOT_DOUBLE_QUOTED = define(
            "nop.err.core.json.strict-mode.string-not-double-quoted", "标准JSON语法要求字符串必须用双引号包裹");

    ErrorCode ERR_JSON_UNEXPECTED_CHAR = define("nop.err.core.json.unexpected-char", "JSON文件格式不正确");

    ErrorCode ERR_JSON_SERIALIZE_STATE_FAIL = define("nop.err.core.json.serialize-state-fail", "JSON序列化状态错误", ARG_KEY);

    ErrorCode ERR_JSON_DUPLICATE_KEY = define("nop.err.core.json.duplicate-key", "JSON对象的属性名[{key}]重复", ARG_KEY);

    ErrorCode ERR_JSON_UNKNOWN_BEAN_PROP = define("nop.err.core.json.unknown-bean-prop", "JSON对象上没有定义属性[{propName}]",
            ARG_PROP_NAME);

    ErrorCode ERR_JSON_UNKNOWN_SERIALIZER_FOR_BEAN = define("nop.err.core.json.unknown-serializer-for-bean",
            "类[{className}]指定的序列化处理器[{serializer}]没有注册", ARG_CLASS_NAME, ARG_SERIALIZER);

    ErrorCode ERR_JSON_UNKNOWN_DESERIALIZER_FOR_BEAN = define("nop.err.core.json.unknown-deserializer-for-bean",
            "类[{className}]指定的序列化处理器[{deserializer}]没有注册", ARG_CLASS_NAME, ARG_SERIALIZER);

    ErrorCode ERR_JSON_UNKNOWN_SERIALIZER_FOR_PROP = define("nop.err.core.json.unknown-serializer-for-prop",
            "类[{className}]的属性[{propName}]指定的序列化处理器[{serializer}]没有注册", ARG_CLASS_NAME, ARG_PROP_NAME, ARG_SERIALIZER);

    ErrorCode ERR_JSON_UNKNOWN_DESERIALIZER_FOR_PROP = define("nop.err.core.json.unknown-deserializer-for-prop",
            "类[{className}]的属性[{propName}]指定的反序列化处理器[{deserializer}]没有注册", ARG_CLASS_NAME, ARG_PROP_NAME,
            ARG_DESERIALIZER);

    ErrorCode ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE = define("nop.err.core.json.only-data-bean-is-serializable",
            "只有标记为DataBean的数据类型才允许参与JSON序列化", ARG_CLASS_NAME);

    ErrorCode ERR_JSON_PROP_NOT_STRING = define("nop.err.core.json.prop-not-string", "属性[{propName}]的值不是字符串类型",
            ARG_PROP_NAME);

    ErrorCode ERR_XML_TO_JSON_OUTPUT_NOT_SUPPORT_MIX_TEXT_NODE = define(
            "nop.err.core.xml-to-json-output-not-support-mix-text-node", "将XML转换为JSON时不支持混合文本节点");

    ErrorCode ERR_XML_TO_JSON_OUTPUT_ONLY_SUPPORT_SIMPLE_TEXT_NODE = define(
            "nop.err.core.xml-to-json-output-only-support-simple-text-node", "将XML转换为JSON时只有简单节点允许文本内容");

    ErrorCode ERR_REFLECT_UNKNOWN_BEAN_PROP = define("nop.err.core.reflect.unknown-bean-prop",
            "类型为[{className}]的对象不支持属性:{propName}", ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_REFLECT_CLASS_NO_DEFAULT_CONSTRUCTOR = define("nop.err.core.class-no-default-constructor",
            "类[className]没有缺省构造函数", ARG_CLASS_NAME);

    ErrorCode ERR_REFLECT_CLASS_MODEL_ALREADY_REGISTERED = define("nop.err.core.reflect.class-model-already-registered",
            "类[{className}]的反射模型已经存在，不允许重复注册", ARG_CLASS_NAME);

    ErrorCode ERR_REFLECT_MODEL_IS_READONLY = define("nop.err.core.reflect.model-is-readonly", "只读模型不允许修改");

    ErrorCode ERR_REFLECT_INVOKE_WITH_INVALID_ARG_COUNT = define("nop.err.core.reflect.invoke-with-arg-count",
            "调用函数[{className}.{methodName}]的个数不匹配", ARG_CLASS_NAME, ARG_METHOD_NAME, ARG_COUNT);

    ErrorCode ERR_REFLECT_SET_PROP_FAIL = define("nop.err.core.reflect.set-prop-fail", "设置属性失败:{propPath}", ARG_BEAN,
            ARG_PROP_PATH);

    ErrorCode ERR_REFLECT_SET_PROP_ON_NULL_OBJ = define("nop.err.core.reflect.set-prop-on-null-obj",
            "设置属性[{propName}]失败，对象为空", ARG_BEAN, ARG_PROP_NAME);

    ErrorCode ERR_REFLECT_BEAN_NOT_COLLECTION_FOR_GETTER = define("nop.err.core.reflect.bean-not-collection-for-getter",
            "获取集合元素失败，{bean}不是集合类型的对象", ARG_BEAN, ARG_INDEX);

    ErrorCode ERR_REFLECT_BEAN_NOT_COLLECTION_FOR_SETTER = define("nop.err.core.reflect.bean-not-collection-for-setter",
            "设置集合元素失败，{bean}不是集合类型的对象", ARG_BEAN, ARG_INDEX);

    ErrorCode ERR_REFLECT_BEAN_PROP_NOT_READABLE = define("nop.err.core.reflect.bean-prop-not-readable",
            "类[{className}]的属性[{propName}]不可读", ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_REFLECT_BEAN_PROP_NOT_WRITABLE = define("nop.err.core.reflect.bean-prop-not-writable",
            "类[{className}]的属性[{propName}]不可写", ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_REFLECT_BEAN_NO_CLASS_FOR_TYPE = define("nop.err.core.reflect.bean-no-class-for-type",
            "{typeValue}没有对应的实现类", ARG_CLASS_NAME, ARG_TYPE_VALUE);

    ErrorCode ERR_REFLECT_BEAN_CLASS_NO_FACTORY_METHOD = define("nop.err.core.reflect.bean-class-no-factory-method",
            "{className}类没有定义工厂方法，也不是枚举类", ARG_CLASS_NAME);

    ErrorCode ERR_REFLECT_BEAN_NO_DEFAULT_CONSTRUCTOR = define("nop.err.core.reflect.bean-no-default-constructor",
            "类[{className}]没有缺省构造器", ARG_CLASS_NAME);

    ErrorCode ERR_REFLECT_TREE_BEAN_NOT_SIMPLE_VALUE = define("nop.err.core.reflect.tree-bean-not-simple-value",
            "TreeBean具有属性或者子节点，不是简单数据类型:{}", ARG_BEAN);
    ErrorCode ERR_REFLECT_BEAN_NOT_SUPPORT_GET_BY_INDEX = define("nop.err.core.reflect.not-support-get-by-index",
            "[{className}]不是数组或者列表类型，不支持按照下标获取", ARG_CLASS_NAME);

    ErrorCode ERR_REFLECT_BEAN_NOT_SUPPORT_SET_BY_INDEX = define("nop.err.core.reflect.not-support-set-by-index",
            "[{className}]不是数组或者列表类型，不支持按照下标设置值", ARG_CLASS_NAME);

    ErrorCode ERR_REFLECT_INVALID_MACRO_METHOD = define("nop.err.core.reflect.invalid-macro-method",
            "[{className}]类中的方法[{methodName}]标注了@Macro注解，但是函数声明不符合Macro方法要求", ARG_CLASS_NAME, ARG_METHOD_NAME);

    ErrorCode ERR_REFLECT_INVALID_TEMPLATE_STRING_METHOD = define("nop.err.core.reflect.invalid-template-string-method",
            "[{className}]类中的方法[{methodName}]标注了@TemplateStringMethod注解，" + "但是函数声明不符合模板字符串方法要求", ARG_CLASS_NAME,
            ARG_METHOD_NAME);

    ErrorCode ERR_REFLECT_INVALID_VALUE_TEXT_FOR_BEAN = define("nop.err.core.reflect.invalid-value-text-for-bean",
            "{text}不是{className}类中定义的合法值", ARG_TEXT, ARG_CLASS_NAME);
    ErrorCode ERR_BEAN_SET_BY_INDEX_NOT_IN_RANGE = define("nop.err.core.bean.set-by-index-not-in-range",
            "setByIndex的参数[{index}]不在有效范围内", ARG_INDEX);

    ErrorCode ERR_REFLECT_COPY_BEAN_ARRAY_LENGTH_NOT_MATCH = define(
            "nop.err.core.reflect.copy-bean-array-length-not-match",
            "数组拷贝的源数组和目标数组的长度不匹配:src={srcLength},target={targetLength}", ARG_SRC_LENGTH, ARG_TARGET_LENGTH);

    ErrorCode ERR_REFLECT_BEAN_PROP_ALIAS_CONFLICT = define("nop.err.core.reflect.bean-prop-alias-conflict",
            "属性[{propName}]的别名[{alias}]与已有别名冲突", ARG_PROP_NAME, ARG_ALIAS);

    ErrorCode ERR_REFLECT_PARSE_PROP_PATH_FAIL = define("nop.err.core.reflect.parse-prop-path-fail", "非法的属性路径");

    ErrorCode ERR_REFLECT_MULTIPLE_FACTORY_METHOD = define("nop.err.core.reflect.multiple-factory-method",
            "不允许多个方法标记为FactoryMethod");

    ErrorCode ERR_REFLECT_NOT_SUPPORTED_METHOD_FOR_SYNTHESIZED_ANNOTATION = define(
            "nop.err.core.reflect.not-supported-method-for-synthesized-annotation",
            "注解类[{className}]不支持注解方法[{methodName}]", ARG_METHOD_NAME, ARG_CLASS_NAME);

    ErrorCode ERR_REFLECT_NO_METHOD_FOR_GIVEN_NAME_AND_ARG_COUNT =
            define("nop.err.core.reflect.no-method-for-given-name-and-arg-count",
                    "类[{className}]中名称为[{methodName}]，参数个数为[{count}]的函数不存在或者存在多个无法确定使用哪一个函数",
                    ARG_CLASS_NAME, ARG_METHOD_NAME, ARG_COUNT);

    ErrorCode ERR_FILTER_INVALID_VALUE_FORMAT = define("nop.err.core.filter.invalid-value-format",
            "属性[{name}]的值[{value}]格式不正确", ARG_NAME, ARG_VALUE);

    ErrorCode ERR_FILTER_OP_IS_NULL = define("nop.err.core.filter.op-is-null", "判断条件没有指定类型");

    ErrorCode ERR_FILTER_OP_NOT_ALLOW_CONTENT = define("nop.err.core.filter.op-not-allow-content",
            "判断节点只允许包含子节点，不允许文本内容");

    ErrorCode ERR_FILTER_UNKNOWN_OP = define("nop.err.core.filter.unknown-op", "不支持的判断条件类型:{op}", ARG_OP);

    ErrorCode ERR_FILTER_NO_NAME_ARG = define("nop.err.core.filter.no-name-arg", "类型为[{op}]的判断条件没有指定变量名参数", ARG_OP);

    // ErrorCode ERR_FILTER_REL_OP_NO_REF_ARG =
    // define("nop.err.core.filter.rel-op-no-ref-arg",
    // "类型为[{op}]的关联判断条件没有指定左值参数名", ARG_OP);
    //
    // ErrorCode ERR_FILTER_REL_OP_NO_RIGHT_ARG =
    // define("nop.err.core.filter.rel-op-no-right-arg",
    // "类型为[{op}]的关联判断条件没有指定右值参数名", ARG_OP);

    ErrorCode ERR_FILTER_SQL_OP_INVALID_VALUE_TYPE = define("nop.err.core.filter.filter-sql-op-invalid-value-type",
            "sql过滤条件的value属性不是SQL数据类型：{value}", ARG_VALUE);

    ErrorCode ERR_QUERY_INVALID_ORDER_BY_SQL = define("nop.err.core.query.invalid-order-by-sql", "排序语句格式不正确");

    ErrorCode ERR_TABLE_INVALID_ROW_RANGE = define("nop.err.core.table.invalid-row-range", "行区域表达式不合法");

    ErrorCode ERR_TABLE_INVALID_CELL_POSITION = define("nop.err.core.table.invalid-cell-position",
            "单元格位置表达式不合法:rowIndex={},colIndex={}", ARG_ROW_INDEX, ARG_COL_INDEX);

    ErrorCode ERR_TABLE_NULL_ROW =
            define("nop.err.core.table.null-row", "下标为[{rowIndex}]的行为null", ARG_ROW_INDEX);

    ErrorCode ERR_TABlE_INVALID_RANGE = define("nop.err.core.table.invalid-range", "表格区域表达式不合法");

    ErrorCode ERR_TABLE_ROW_NOT_BIND_TO_TABLE = define("nop.err.core.table.row-not-bind-to-table",
            "Row必须加入某个表格，然后才能执行此操作", ARG_ROW);

    ErrorCode ERR_TABLE_NOT_TREE_CELL =
            define("nop.err.core.table.not-tree-cell", "单元格[{cellPos}]必须在父单元格的范围之内", ARG_CELL_POS);

    ErrorCode ERR_TABLE_NOT_PROXY_CELL = define("nop.err.core.table.not-proxy-cell",
            "位置在({rowIndex},{colIndex})的单元格不是ProxyCell", ARG_ROW_INDEX, ARG_COL_INDEX);

    ErrorCode ERR_TABLE_INVALID_PROXY_CELL = define("nop.err.core.table.invalid-proxy-cell",
            "位置在({rowIndex},{colIndex})的ProxyCell不合法", ARG_ROW_INDEX, ARG_COL_INDEX, ARG_CELL);

    ErrorCode ERR_TABLE_MERGE_CELL_EMPTY_OR_PROXY_CELL = define("nop.err.core.table.merge-cell-empty-or-proxy-cell",
            "合并单元格不能是null或者ProxyCell", ARG_ROW_INDEX, ARG_COL_INDEX, ARG_CELL);

    ErrorCode ERR_TABLE_NO_ENOUGH_FREE_SPACE = define("nop.err.core.table.no-enough-free-space",
            "表格位置({rowIndex},{colIndex})处没有足够的未占用空间:mergeDown={mergeDown},mergeAcross={mergeAcross}", ARG_ROW_INDEX,
            ARG_COL_INDEX, ARG_MERGE_DOWN, ARG_MERGE_ACROSS);

    ErrorCode ERR_BEAN_UNKNOWN_PROP = define("nop.err.core.bean.unknown-prop", "类[{className}]上不存在属性[{propName}]",
            ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_LANG_AST_IS_READ_ONLY = define("nop.err.core.ast-is-read-only", "语法树已经标记为只读，不允许修改");

    ErrorCode ERR_LANG_AST_PARENT_ALREADY_BOUND = define("nop.err.core.ast-parent-already-bound",
            "语法树节点已经绑定到其他父节点。一个节点步允许拥有多个父节点");

    ErrorCode ERR_TYPE_NOT_ARRAY_OR_LIST_TYPE = define("nop.err.core.type.not-array-or-list-type",
            "{typeName}不是数组类型或者列表类型", ARG_TYPE_NAME);

    ErrorCode ERR_TYPE_NOT_MAP_TYPE = define("nop.err.core.type.not-map-type", "{typeName}不是Map类型", ARG_TYPE_NAME);

    ErrorCode ERR_TYPE_NOT_FUNCTION_TYPE = define("nop.err.core.type.not-function-type", "{typeName}不是函数类型",
            ARG_TYPE_NAME);

    ErrorCode ERR_TYPE_NOT_ALLOW_FUNCTION_TYPE_ARRAY = define("nop.err.core.type.not-allow-function-type-array",
            "{typeName}是函数类型，不支持函数类型的数组", ARG_TYPE_NAME);

    ErrorCode ERR_TYPE_NOT_TUPLE_TYPE = define("nop.err.core.type.not-tuple-type", "{typeName}不是Tuple类型",
            ARG_TYPE_NAME);

    ErrorCode ERR_TYPE_NOT_STRUCTURE_TYPE = define("nop.err.core.type.not-structure-type",
            "{typeName}不是Union/Intersection/Tuple类型", ARG_TYPE_NAME);

    ErrorCode ERR_TYPE_NOT_PREDEFINED_TYPE = define("nop.err.core.type.not-predefined-type", "{typeName}不是预定义的泛型类型",
            ARG_TYPE_NAME);

    ErrorCode ERR_TYPE_UNSUPPORTED_JAVA_TYPE = define("nop.err.core.type.unsupported-java-type", "不支持的Java类型",
            ARG_TYPE_NAME);

    ErrorCode ERR_TYPE_REDEFINE_TYPE_VARIABLE = define("nop.err.core.type.redefine-type-variable",
            "类型变量[{varName}]已经存在，不允许重复定义", ARG_VAR_NAME);

    ErrorCode ERR_TYPE_MULTIPLE_RAW_TYPE_HAS_SAME_NAME = define("nop.err.core.type.multiple-raw-type-has-same-name",
            "同一个类名[{className}]不能对应多个RawType对象", ARG_CLASS_NAME);

    ErrorCode ERR_TYPE_NOT_TYPE_VARIABLE = define("nop.err.core.type.not-type-variable", "[{typeName}]不是类型变量",
            ARG_TYPE_NAME);

    ErrorCode ERR_TYPE_REFERENCE_NOT_RESOLVED = define("nop.err.core.type.type-reference-not-resolved",
            "引用了未定义的类型变量:{typeName}", ARG_TYPE_NAME);

    ErrorCode ERR_LANG_DECODE_INVALID_MESSAGE = define("nop.err.lang.decode.invalid-message", "消息格式不正确，无法解码");

    ErrorCode ERR_JSON_UNKNOWN_BIND_EXPR_TYPE = define("nop.err.json.unknown-bind-expr-type", "未知的绑定表达式类型:{type}",
            ARG_TYPE);

    ErrorCode ERR_JSON_UNKNOWN_VALUE_PATTERN_TYPE = define("nop.err.json.unknown-value-pattern-type", "未知的模式类型:{type}",
            ARG_TYPE);

    ErrorCode ERR_GRAPH_DUPLICATE_VERTEX = define("nop.err.graph.duplicate-vertex", "图节点已经存在");

    ErrorCode ERR_GRAPH_UNKNOWN_NODE = define("nop.err.graph.unknown-node", "未知的节点：{name}", ARG_NAME);

    ErrorCode ERR_GRAPH_NODES_NOT_REACHABLE = define("nop.err.graph.nodes-not-reachable", "从起始节点触发无法到达以下节点:{nodeNames}", ARG_NODE_NAMES);

    ErrorCode ERR_UNITTEST_UNKNOWN_MARKDOWN_SECTION = define("nop.err.core.unittest.unknown-markdown-section",
            "Markdown测试文件[{fileName}]中没有找到标题为[{title}]的部分", ARG_FILE_NAME, ARG_TITLE);

    ErrorCode ERR_UNITTEST_EXCEPTION_WITH_ERROR_CODE_EXPECTED = define(
            "nop.err.core.unittest.exception-with-error-code-expected", "测试用例应该抛出异常，期待的异常码为[{errorCode}]",
            ARG_FILE_NAME, ARG_TITLE, ARG_ERROR_CODE);

    ErrorCode ERR_UNITTEST_EXCEPTION_EXPECTED = define("nop.err.core.unittest.exception-with-error-code-expected",
            "测试用例应该抛出异常，期待的异常消息为[{exception}]", ARG_FILE_NAME, ARG_TITLE, ARG_EXCEPTION);

    ErrorCode ERR_UNITTEST_RETURN_VALUE_MISMATCH = define("nop.err.core.unittest.return-value-mismatch",
            "测试用例的返回值不符合预期，期待的结果为[{value}]", ARG_FILE_NAME, ARG_TITLE, ARG_VALUE);

    ErrorCode ERR_UNITTEST_INVALID_MARKDOWN_TEST_SECTION = define("nop.err.core.unittest.invalid-markdown-test-section",
            "解析测试用例文件失败", ARG_FILE_NAME);

    ErrorCode ERR_RPC_CLIENT_PREV_CALL_NOT_COMPLETED = define("nop.err.core.rpc.prev-call-not-completed",
            "此前发起的RPC调用尚未完成，无法再发起新的调用", ARG_SERVICE_NAME, ARG_SERVICE_METHOD);

    ErrorCode ERR_I18N_INVALID_MESSAGE_VALUE = define("nop.err.core.i18n.invalid-message-value",
            "i18n消息字符串[{key}]的值[{value}]不是字符串类型", ARG_KEY, ARG_VALUE);

    ErrorCode ERR_I18N_DUPLICATED_MESSAGE_KEY = define("nop.err.core.i18n.duplicated-message-key",
            "i18n消息字符串[{key}]的定义重复:locA={locA},valueA={valueA},locB={locB},valueB={valueB}", ARG_KEY, ARG_LOC_A,
            ARG_VALUE_A, ARG_LOC_B, ARG_VALUE_B);

    ErrorCode ERR_DICT_UNKNOWN_DICT = define("nop.err.core.dict.unknown-dict", "未知的字典:{dictName}", ARG_DICT_NAME);

    ErrorCode ERR_DICT_BUILD_DICT_FROM_ENUM_FAIL = define("nop.err.core.dict.build-dict-from-enum-fail",
            "装载枚举类[{className}]失败", ARG_CLASS_NAME);

    ErrorCode ERR_DICT_NOT_VALID_ENUM_CLASS = define("nop.err.core.dict.not-valid-enum-class", "[{className}]不是合法的枚举类",
            ARG_CLASS_NAME);

    ErrorCode ERR_VALIDATE_CHECK_FAIL = define("nop.err.core.validate.check-fail", "验证失败");

    ErrorCode ERR_SELECTION_INVALID_ARG_NAME = define("nop.err.core.selection.invalid-arg-name",
            "参数名[{name}]不合法，它必须满足Java语法规范且不能以$为前缀", ARG_NAME);

    ErrorCode ERR_SQL_PARAM_COUNT_MISMATCH = define("nop.err.core.sql.sql-param-count-mismatch", "SQL参数个数不匹配",
            ARG_COUNT);

    ErrorCode ERR_SQL_FILTER_INVALID_FIELD_NAME = define("nop.err.core.sql.sql-filter-invalid-field-name",
            "SQL字段名不合法:{name}", ARG_NAME);

    ErrorCode ERR_CORE_NO_API_SERVICE_CONTEXT = define("nop.err.core.no-api-service-context", "缺少没有Api服务上下文");

    ErrorCode ERR_DELTA_MERGE_NODE_NOT_INHERIT = define("nop.err.core.delta-merge-node-not-inherit",
            "路径为[{jsonPath}]的节点没有对应可继承的父节点", ARG_JSON_PATH);

    ErrorCode ERR_XML_NOT_ALLOW_CUSTOM_NAMESPACE =
            define("nop.err.core.xml.not-allow-custom-namespace",
                    "[{xmlName}]不允许自定义的名字空间，允许的名字空间为:{allowedNs}", ARG_XML_NAME, ARG_ALLOWED_NS);

    ErrorCode ERR_XML_NOT_ALLOW_COMPILE_PHASE_EXPR =
            define("nop.err.core.xml.not-allow-compile-phase-expr",
                    "不允许使用编译期表达式:{expr}", ARG_EXPR);

    ErrorCode ERR_XML_NOT_ALLOW_EXPR =
            define("nop.err.core.xml.not-allow-expr",
                    "不允许使用表达式:{expr}", ARG_EXPR);

    ErrorCode ERR_TREE_DUPLICATE_NODE_ID =
            define("nop.err.tree.duplicate-node-id",
                    "多个节点具有同样的id:{}", ARG_NODE_ID);
}