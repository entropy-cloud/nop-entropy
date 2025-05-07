/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface XLangErrors {
    String ARG_OP = "op";
    String ARG_EXPECTED = "expected";
    String ARG_PEEK_OP = "peekOp";
    String ARG_TOKEN = "token";

    String ARG_PARSE_TREE = "parseTree";

    String ARG_NODE = "node";
    String ARG_NODES = "nodes";

    String ARG_XDEF_NODE = "xdefNode";
    String ARG_XDEF_NODE_NAME = "xdefNodeName";

    String ARG_XDEF_PATH = "xdefPath";

    String ARG_OUTPUT_MODE = "outputMode";

    String ARG_ATTR_NAME = "attrName";
    String ARG_ALLOWED_NAMES = "allowedNames";
    String ARG_ALLOWED_VALUES = "allowedValues";

    String ARG_VALUE = "value";
    String ARG_NAMES = "names";

    String ARG_MIN_VALUE = "minValue";
    String ARG_MAX_VALUE = "maxValue";
    String ARG_EXCLUDE_MIN = "excludeMin";
    String ARG_EXCLUDE_MAX = "excludeMax";

    String ARG_MIN_LENGTH = "minLength";
    String ARG_MAX_LENGTH = "maxLength";

    String ARG_ITEM_VALUE = "itemValue";

    String ARG_DICT_NAME = "dictName";

    // String ARG_SLOT_NAME = "slotName";
    String ARG_LIB_PATH = "libPath";
    String ARG_TAG_NAME = "tagName";
    String ARG_TAG_NAME_EXPR = "tagNameExpr";
    String ARG_EXPR = "expr";
    String ARG_MSG_EXPR = "msgExpr";
    String ARG_PARENT_EXPR = "parentExpr";

    String ARG_TYPE_NAME = "typeName";
    String ARG_ATTR_VALUE = "attrValue";
    String ARG_PATH = "path";
    String ARG_NS = "ns";

    String ARG_DECORATED = "decorated";

    String ARG_LANG = "lang";

    String ARG_SCRIPT_LANGS = "scriptLangs";

    String ARG_ALIAS = "alias";
    String ARG_CLASS1 = "class1";
    String ARG_CLASS2 = "class2";
    String ARG_TAG1 = "tag1";
    String ARG_TAG2 = "tag2";
    String ARG_VAR_DECL1 = "varDecl1";
    String ARG_VAR_DECL2 = "varDecl2";

    String ARG_VAR_NAME = "varName";

    String ARG_NAME = "name";

    String ARG_PARAM_NAME = "paramName";
    String ARG_OVERRIDE = "override";
    String ARG_NODE_A = "nodeA";
    String ARG_NODE_B = "nodeB";
    String ARG_KEY = "key";

    String ARG_OTHER_NODE = "otherNode";

    String ARG_ENUM_CLASS = "enumClass";

    String ARG_REF_NAME = "refName";
    String ARG_LOC_A = "locA";
    String ARG_LOC_B = "locB";
    String ARG_TYPE_A = "typeA";
    String ARG_TYPE_B = "typeB";

    String ARG_ID = "id";

    String ARG_PROP_NAME = "propName";
    String ARG_CLASS_NAME = "className";
    String ARG_METHOD_NAME = "methodName";
    String ARG_ARG_COUNT = "argCount";

    String ARG_INJECT_PARAM = "injectParam";

    String ARG_PROP = "prop";
    String ARG_REF_PROP = "refProp";

    String ARG_OBJ_EXPR = "objExpr";
    String ARG_ATTR_EXPR = "attrExpr";

    String ARG_FUNC_NAME = "funcName";
    String ARG_FUNC_EXPR = "funcExpr";
    String ARG_ERROR = "error";
    String ARG_MAX_COUNT = "maxCount";
    String ARG_MIN_COUNT = "minCount";

    String ARG_DEF_LOC = "defLoc";

    String ARG_CALL_LOC = "callLoc";

    String ARG_ARGS = "args";
    String ARG_VALUE_EXPR = "valueExpr";
    String ARG_AST_NODE = "astNode";

    String ARG_IDENTIFIER_KIND = "identifierKind";

    String ARG_DECL_LOC = "declLoc";
    String ARG_USE_LOC = "useLoc";

    String ARG_DETAIL = "detail";

    String ARG_SLOT_NAME = "slotName";
    String ARG_SLOT_TYPE = "slotType";

    String ARG_EXPECTED_TYPE = "expectedType";
    String ARG_ACTUAL_TYPE = "actualType";

    String ARG_RESOURCE_NAME = "resourceName";

    String ARG_ARG_NAME = "argName";

    String ARG_STD_DOMAIN = "stdDomain";
    String ARG_OPTIONS = "options";

    String ARG_DEF_TYPE = "defType";

    String ARG_SUB_TYPE_PROP = "subTypeProp";
    String ARG_SUB_TYPE_VALUE = "subTypeValue";
    String ARG_SCHEMA_KIND = "schemaKind";

    String ARG_SCHEMA_PATH = "schemaPath";
    String ARG_REQUIRED_SCHEMA = "requiredSchema";

    String ARG_BEAN_CLASS = "beanClass";

    String ARG_TARGET = "target";
    String ARG_TARGET_TYPE = "targetType";

    String ARG_PATTERN = "pattern";

    String ARG_CONFIG_VARS = "configVars";

    String ARG_OBJ_NAME = "objName";

    String ARG_ERR_MSG = "errMsg";

    String ARG_CODE = "code";

    String ARG_METHOD_REF = "methodRef";

    ErrorCode ERR_EXPR_PARSE_NOT_END_PROPERLY = define("nop.err.xlang.expr.not-end-properly", "解析失败，表达式没有正常结束");

    ErrorCode ERR_EXPR_UNSUPPORTED_OP = define("nop.err.xlang.expr.unsupported-op", "不支持的运算符:{op}", ARG_OP);

    ErrorCode ERR_EXPR_NOT_EXPECTED_OP = define("nop.err.xlang.expr.not-expected-op", "不是期望的运算符:{expected}",
            ARG_EXPECTED);

    ErrorCode ERR_EXPR_BINARY_OP_NO_RIGHT_VALUE = define("nop.err.xlang.expr.binary-op-no-right-value",
            "二元操作符[{op}]没有有效的右侧表达式", ARG_OP);

    ErrorCode ERR_EXPR_BINARY_OP_NO_LEFT_VALUE = define("nop.err.xlang.expr.binary-op-no-left-value",
            "二元操作符[{op}]没有有效的左侧表达式", ARG_OP);

    ErrorCode ERR_EXPR_NULL_FACTOR = define("nop.err.xlang.expr.null-factor", "操作符[{op}]的子表达式不能为null", ARG_OP);

    ErrorCode ERR_EXPR_NOT_SINGLE_EXPR = define("nop.err.xlang.expr.not-single-expr",
            "只允许出现一个表达式，不允许文本和表达式混排，或者出现多个表达式");

    ErrorCode ERR_EXPR_NOT_CP_EXPR = define("nop.err.xlang.expr.not-cp-expr", "只允许编译期表达式");

    ErrorCode ERR_EXPR_NOT_ALLOW_CP_EXPR = define("nop.err.xlang.expr.not-allow-cp-expr", "不支持编译期表达式");

    ErrorCode ERR_EXPR_NOT_LITERAL = define("nop.err.xlang.expr.not-literal", "表达式必须返回固定值");

    ErrorCode ERR_EXPR_UNEXPECTED_CHAR = define("nop.err.xlang.expr.unexpected-char", "非法的字符");

    ErrorCode ERR_EXPR_UNARY_OP_NO_EXPR = define("nop.err.xlang.expr.unary-op-no-expr", "运算符[{op}]后没有合法的表达式", ARG_OP);

    ErrorCode ERR_EXPR_JSON_LITERAL_NOT_ALLOW_PROP_ACCESS = define(
            "nop.err.xlang.expr.json-literal-not-allow-prop-access", "JSON字面量不允许直接直接访问属性");

    ErrorCode ERR_EXPR_EMPTY_BRACE = define("nop.err.xlang.expr.empty-brace", "括号内表达式不能为空");

    ErrorCode ERR_EXPR_MISSING_FACTOR = define("nop.err.xlang.expr.missing-factor", "表达式部分缺失");

    ErrorCode ERR_EXPR_INVALID_ATTR_EXPR = define("nop.err.xlang.expr.invalid-attr-expr", "解析属性表达式失败");

    ErrorCode ERR_EXPR_XPL_DUPLICATE_ATTR_NAME = define("nop.err.xlang.expr.xpl-duplicate-attr-name", "Xpl表达式的属性名重复",
            ARG_ATTR_NAME);

    ErrorCode ERR_EXPR_TOKEN_SHOULD_NOT_BE_KEYWORD = define("nop.err.xlang.expr.token-should-not-be-keyword",
            "变量名[{token}]不允许为关键字", ARG_TOKEN);

    ErrorCode ERR_EXPR_NOT_EXECUTABLE = define("nop.err.xlang.expr.not-executable", "不是可执行的表达式");

    ErrorCode ERR_XLANG_INVALID_METHOD_REF = define("nop.err.xlang.invalid-method-ref", "非法的方法引用:{methodRef}", ARG_METHOD_REF);

    ErrorCode ERR_XLANG_INVALID_PARSE_TREE = define("nop.err.xlang.invalid-parse-tree", "非法的解析树结构", ARG_TOKEN);

    ErrorCode ERR_XLANG_INVALID_VAR_NAME = define("nop.err.xlang.invalid-var-name", "不合法的变量名:{varName}", ARG_VAR_NAME);

    ErrorCode ERR_XLANG_UNSUPPORTED_OP = define("nop.err.xlang.unsupported-op", "不支持的运算符:{op}", ARG_OP);

    ErrorCode ERR_XLANG_IMPORT_MULTIPLE_CLASS_CONFLICTS = define("nop.err.xlang.import-multiple-class-conflicts",
            "导入类[{class1}]和[{class2}]的别名都为{alias}，出现命名冲突", ARG_CLASS1, ARG_CLASS2, ARG_ALIAS);

    ErrorCode ERR_XLANG_IMPORT_MULTIPLE_LIB_CONFLICTS = define("nop.err.xlang.import-multiple-lib-conflicts",
            "导入标签[{tag1}]和[{tag2}]时出现命名冲突", ARG_TAG1, ARG_TAG2);

    ErrorCode ERR_XLANG_DECLARE_VAR_CONFLICTS = define("nop.err.xlang.declare-var-conflicts",
            "变量定义[{varName}]命名冲突，冲突位置为[{varDecl1}]和[{varDecl2}]", ARG_VAR_DECL1, ARG_VAR_DECL2, ARG_VAR_NAME);

    ErrorCode ERR_XLANG_SCOPE_VAR_DEFINITION_CONFLICTS = define("nop.err.xlang.scope-var-definition-conflicts",
            "scope变量定义[{varName}]命名冲突，冲突位置为[{varDecl1}]和[{varDecl2}]", ARG_VAR_DECL1, ARG_VAR_DECL2, ARG_VAR_NAME);

    ErrorCode ERR_XLANG_USE_VAR_BEFORE_DECLARATION = define("nop.err.xlang.use-var-before-declaration",
            "在变量定义前使用变量:{varName},定义位置:{declLoc},使用位置:{useLoc}", ARG_VAR_NAME, ARG_USE_LOC, ARG_DECL_LOC);

    ErrorCode ERR_XLANG_UNRESOLVED_IDENTIFIER = define("nop.err.xlang.unresolved-identifier", "未定义的变量:{varName}",
            ARG_VAR_NAME);

    ErrorCode ERR_XLANG_UNRESOLVED_IMPLICIT_VAR = define("nop.err.xlang.unresolved-implicit-var", "未定义的隐式变量:{varName}",
            ARG_VAR_NAME);

    ErrorCode ERR_XLANG_UNRESOLVED_TYPE = define("nop.err.xlang.unresolved-type", "未知的类型:{typeName}", ARG_TYPE_NAME);

    ErrorCode ERR_XLANG_IDENTIFIER_NOT_FUNCTION = define("nop.err.xlang.identifier-not-function", "变量{varName}不是函数",
            ARG_VAR_NAME);

    ErrorCode ERR_XLANG_IDENTIFIER_IS_KEYWORD = define("nop.err.xlang.identifier-is-keyword",
            "标识符名称[{name}]不能是系统保留的关键字", ARG_NAME);

    ErrorCode ERR_XLANG_IMPORTED_CLASS_MUST_STARTS_WITH_UPPERCASE = define(
            "nop.err.xlang.imported-class-alias-must-starts-with-uppercase", "导入类的别名首字母必须大写", ARG_ALIAS);

    ErrorCode ERR_XLANG_IMPORT_CLASS_ALIAS_IS_BUILT_IN_TYPE_NAME = define(
            "nop.err.xlang.imported-class-alias-is-built-in-type-name", "导入类的别名不允许是系统内置类型名称：{alias}", ARG_ALIAS);

    ErrorCode ERR_XLANG_GLOBAL_VAR_NOT_ALLOW_CHANGE = define("nop.err.xlang.global-var-not-allow-change",
            "全局变量不允许在脚本中通过赋值语句进行修改", ARG_NAME);

    ErrorCode ERR_XLANG_TEMPLATE_EXPR_ID_MUST_BE_MACRO_FUNCTION = define(
            "nop.err.xlang.template-expr-id-must-be-macro-function", "模板表达式的id必须是宏函数的函数名", ARG_NAME);

    // ErrorCode ERR_XLANG_IMPORTED_CLASS_NOT_ALLOW_USED_AS_PARAM =
    // define("nop.err.xlang.imported-class-not-allow-used-as-param",
    // "导入的类名[{className}]不允许作为函数参数或者赋值参数使用", ARG_CLASS_NAME);

    ErrorCode ERR_XLANG_IDENTIFIER_NOT_ALLOW_CHANGE = define("nop.err.xlang.identifier-not-allow-change",
            "常量变量[{name}]不允许被修改", ARG_NAME);

    ErrorCode ERR_XLANG_FUNC_DECL_NOT_ALLOW_PREFIX_G = define("nop.err.xlang.func-decl-not-allow-prefix-g",
            "自定义函数[{funcName}]的名称不应该以g_为前缀，此前缀为全局函数保留", ARG_FUNC_NAME);

    ErrorCode ERR_XLANG_FUNC_DECL_CONFLICT_WITH_GLOBAL_FUNC = define(
            "nop.err.xlang.func-decl-conflict-with-global-func", "自定义函数[{funcName}]的名称与已经注册的全局函数名冲突", ARG_FUNC_NAME);

    ErrorCode ERR_XLANG_PARAM_NAME_CONFLICTED = define("nop.err.xlang.param-name-conflicted", "参数名冲突:{paramName}",
            ARG_PARAM_NAME);

    ErrorCode ERR_XLANG_INITIALIZER_ONLY_ALLOW_LITERAL = define("nop.err.xlang.initializer-only-allow-literal",
            "函数缺省值只允许字面量，不允许使用动态表达式");

    ErrorCode ERR_XLANG_CONST_DECL_NO_INITIALIZER = define("nop.err.xlang.const-decl-no-initializer", "常量声明没有初始化表达式",
            ARG_EXPR);

    ErrorCode ERR_XLANG_VAR_DECL_NO_ALLOW_BINDING = define("nop.err.xlang.var-decl-not-allow-binding",
            "var变量声明不允许解构表达式", ARG_EXPR);

    ErrorCode ERR_XLANG_BREAK_STATEMENT_NOT_IN_LOOP = define("nop.err.xlang.break-statement-not-in-loop",
            "break语句必须放到循环语句内部");

    ErrorCode ERR_XLANG_CONTINUE_STATEMENT_NOT_IN_LOOP = define("nop.err.xlang.continue-statement-not-in-loop",
            "break语句必须放到循环语句内部");

    ErrorCode ERR_MACRO_INVALID_ARG_AST = define("nop.err.xlang.macro.invalid-arg-ast",
            "宏函数的参数[{expr}]的类型不正确，期待类型为[{expectedType}]，实际类型为[{actualType}]", ARG_EXPR, ARG_EXPECTED_TYPE,
            ARG_ACTUAL_TYPE);

    ErrorCode ERR_MACRO_FUNC_ARG_MUST_BE_TEMPLATE_LITERAL_OR_STRING_LITERAL = define(
            "nop.err.xlang.macro.func-arg-must-be-template-literal-or-string-literal", "模板宏函数的参数必须是模板文本类型或者文本类型",
            ARG_EXPR);

    ErrorCode ERR_XPATH_ROOT_NOT_ALLOW_PARENT_SELECTOR = define("nop.err.xlang.xpath.root-not-allow-parent-selector",
            "根节点不支持父节点选择符", ARG_NODE);

    ErrorCode ERR_XPATH_UNKNOWN_OPERATOR = define("nop.err.xlang.xpath.unknown-operator", "未定义的XPath选择符:{op}", ARG_OP);

    ErrorCode ERR_XPL_UNKNOWN_OUTPUT_MODE = define("nop.err.xlang.xpl.unknown-output-mode", "非法的XPL输出模式:{}",
            ARG_OUTPUT_MODE);

    ErrorCode ERR_XPL_UNKNOWN_TAG_ATTR = define("nop.err.xlang.xpl.unknown-tag-attr",
            "标签[{tagName}]不支持属性:{attrName},允许的属性为{allowedNames}", ARG_ATTR_NAME, ARG_ALLOWED_NAMES);

    ErrorCode ERR_XPL_UNKNOWN_TAG_SLOT = define("nop.err.xlang.xpl.unknown-tag-slot",
            "未知的子节点:{slotName},允许的节点名为{allowedNames}", ARG_SLOT_NAME, ARG_ALLOWED_NAMES);

    ErrorCode ERR_XPL_THIS_LIB_NS_ONLY_ALLOWED_IN_TAG_IMPL = define("nop.err.xpl.this-lib-ns-only-allowed-in-tag-impl",
            "thisLib名字空间仅能在表现的实现代码中使用", ARG_TAG_NAME);

    ErrorCode ERR_XPL_UNKNOWN_LIB_TAG = define("nop.err.xlang.xpl.unknown-lib-tag", "标签库[{libPath}]中没有定义标签[{tagName}]",
            ARG_LIB_PATH, ARG_TAG_NAME);

    ErrorCode ERR_XPL_TAG_NO_IMPLICIT_ATTR = define("nop.err.xlang.xpl.tag-no-implicit-attr",
            "标签[{tagName}]的隐式属性[{attrName}]为空，上下文中没有设置同名的变量", ARG_ATTR_NAME, ARG_TAG_NAME);

    ErrorCode ERR_XPL_MULTIPLE_SLOT_WITH_SAME_NAME = define("nop.err.xlang.xpl.multiple-slot-with-same-name",
            "多个子节点名称重复:{tagName}", ARG_TAG_NAME, ARG_NODE);

    ErrorCode ERR_XPL_INVALID_ATTR_EXPR = define("nop.err.xlang.xpl.invalid-attr-expr", "属性表达式不合法：{attrName}",
            ARG_ATTR_NAME);

    ErrorCode ERR_XPL_SCOPE_ARGS_MUST_BE_MAP_EXPR = define("nop.err.xlang.xpl.scope-args-must-be-map-expr",
            "xpl:scopeBinding的内容必须是Map表达式");

    ErrorCode ERR_XPL_PARSE_ATTR_NUM_FAIL = define("nop.err.xlang.xpl.parse-attr-num-fail",
            "解析数字型属性失败:{attrName}={value}", ARG_ATTR_NAME, ARG_VALUE);

    ErrorCode ERR_XPL_PARSE_ATTR_INT_FAIL = define("nop.err.xlang.xpl.parse-attr-int-fail",
            "解析整数类型属性失败:{attrName}={value}", ARG_ATTR_NAME, ARG_VALUE);

    ErrorCode ERR_XPL_ATTR_NOT_ALLOW_EXPR = define("nop.err.xlang.xpl.attr-not-allow-expr",
            "节点属性[{attrName}]不允许是表达式:{value}", ARG_ATTR_NAME, ARG_VALUE);

    ErrorCode ERR_XPL_ATTR_NOT_SIMPLE_EXPR = define("nop.err.xlang.xpl.attr-not-simple-expr",
            "节点属性[{attrName}]不是简单表达式，不需要使用${和}包裹:{value}", ARG_ATTR_NAME, ARG_VALUE);

    ErrorCode ERR_XPL_ATTR_NOT_IDENTIFIER = define("nop.err.xlang.xpl.attr-not-identifier", "节点属性不是合法的变量名",
            ARG_ATTR_NAME, ARG_VALUE);

    ErrorCode ERR_XPL_ATTR_NOT_XML_NAME = define("nop.err.xlang.xpl.attr-not-xml-name", "节点属性不是合法的XML名称", ARG_ATTR_NAME,
            ARG_VALUE);

    ErrorCode ERR_XPL_NOT_XML_NAME = define("nop.err.xlang.xpl.not-xml-name", "不是合法的XML名称:{value}", ARG_VALUE);

    ErrorCode ERR_XPL_TAG_MISSING_ATTRS = define("nop.err.xlang.xpl.tag-missing-attrs", "标签缺少必要属性", ARG_NAMES);

    ErrorCode ERR_XPL_ATTR_NOT_CP_EXPR = define("nop.err.xlang.xpl.attr-not-cp-expr", "属性[{attrName}]的值不是编译期表达式",
            ARG_ATTR_NAME);

    ErrorCode ERR_XPL_FOR_TAG_NOT_ALLOW_BOTH_ITEMS_AND_BEGIN_END = define(
            "nop.err.xlang.xpl.for-tag-not-allow-both-items-and-begin-end", "for标签不允许同时定义items属性以及begin/end属性");

    ErrorCode ERR_XPL_ATTR_MUST_NOT_SYS_IDENTIFIER = define("nop.err.xlang.xpl.attr-must-not-be-sys-identifier",
            "非系统变量的变量名不能以$为前缀:{value}", ARG_ATTR_NAME, ARG_VALUE);

    ErrorCode ERR_XPL_IDENTIFIER_MUST_NOT_KEYWORD = define("nop.err.xlang.xpl.identifier-must-not-be-keyword",
            "变量名不能是XLang语言的关键字", ARG_ATTR_NAME, ARG_VALUE);

    ErrorCode ERR_XPL_MISSING_ATTR = define("nop.err.xlang.xpl.missing-attr", "缺少必要的属性:{attrName}", ARG_ATTR_NAME,
            ARG_NODE);

    ErrorCode ERR_XPL_TAG_MISSING_ATTR = define("nop.err.xlang.xpl.tag-missing-attr", "缺少必要的属性:{attrName}",
            ARG_ATTR_NAME, ARG_TAG_NAME);

    ErrorCode ERR_XPL_MACRO_TAG_ATTR_NOT_STATIC_VALUE = define("nop.err.xlang.xpl.macro-tag-attr-not-static-value",
            "宏标签[{tagName}]的参数[{attrName}]必须是编译期可以确定的静态值", ARG_TAG_NAME, ARG_ATTR_NAME);

    ErrorCode ERR_XPL_TAG_ATTR_IS_MANDATORY = define("nop.err.xlang.xpl.tag-attr-is-mandatory",
            "标签[{tagName}]属性不允许为空:{attrName}", ARG_ATTR_NAME, ARG_TAG_NAME);

    ErrorCode ERR_XPL_TAG_MISSING_SLOT = define("nop.err.xlang.xpl.tag-missing-slot", "缺少必要的slot属性:{slotName}",
            ARG_SLOT_NAME, ARG_TAG_NAME);

    ErrorCode ERR_XPL_TAG_UNKNOWN_SLOT_ARG = define("nop.err.xlang.xpl.unknown-slot-arg",
            "slot[{slotName}]不支持变量[{argName}]", ARG_SLOT_NAME, ARG_ARG_NAME);

    ErrorCode ERR_XPL_TAG_FUNC_IS_COMPILING = define("nop.err.xlang.xpl.tag-func-is-compiling",
            "标签[{tagName}]不支持编译期的嵌套调用，标签正在编译过程中，不允许被调用");

    ErrorCode ERR_XPL_MISSING_SLOT = define("nop.err.xlang.xpl.missing-slot", "缺少必要的子节点:{slotName}", ARG_SLOT_NAME,
            ARG_NODE);

    ErrorCode ERR_XPL_IIF_NODE_MUST_HAS_TWO_CHILD =
            define("nop.err.xlang.xpl.iif-node-child-count-not-two", "c:iif节点的子节点个数必须是2",
                    ARG_NODE);


    ErrorCode ERR_XPL_INVALID_TYPE_REF = define("nop.err.xlang.xpl.invalid-type-ref", "非法的类型定义", ARG_TYPE_NAME);

    ErrorCode ERR_XPL_SCRIPT_NOT_ALLOW_CHILD = define("nop.err.xlang.xpl.script-not-allow-child",
            "script标签不允许包含子节点，只能是文本内容");

    ErrorCode ERR_XPL_EVAL_NOT_ALLOW_CHILD = define("nop.err.xlang.xpl.eval-not-allow-child",
            "eval标签不允许包含子节点，只能是文本内容");

    ErrorCode ERR_XPL_EVAL_INVALID_LANG = define("nop.err.xlang.xpl.eval-invalid-lang", "eval节点的lang属性不合法：{lang}", ARG_LANG);

    ErrorCode ERR_XPL_NOT_ALLOW_OUTPUT = define("nop.err.xlang.xpl.not-allow-output",
            "xpl的当前输出模式为[{outputMode}]，不允许输出文本内容", ARG_OUTPUT_MODE);

    ErrorCode ERR_XPL_NOT_ALLOW_OUTPUT_TAG = define("nop.err.xlang.xpl.not-allow-output-tag",
            "xpl的当前为文本输出模式，不允许输出识别的XML节点");

    ErrorCode ERR_XPL_CHOOSE_CHILD_NOT_CONDITIONAL_EXPR = define("nop.err.xlang.xpl.choose-child-not-conditional-expr",
            "choose标签的子节点必须是条件表达式");

    ErrorCode ERR_XPL_INVALID_VPATH_ATTR = define("nop.err.xlang.xpl.invalid-vpath-attr",
            "属性[attrName]的值[{value}]不是合法的虚拟路径", ARG_ATTR_NAME, ARG_VALUE);

    ErrorCode ERR_XPL_OUT_TAG_NOT_ALLOW_CHILD = define("nop.err.xlang.xpl.out-tag-not-allow-child", "out标签不允许包含子节点");

    ErrorCode ERR_XPL_LOG_TAG_NOT_ALLOW_CHILD = define("nop.err.xlang.xpl.log-tag-not-allow-child", "log标签不允许包含子节点");

    ErrorCode ERR_XPL_ENUM_NO_FACTORY_METHOD = define("nop.err.xlang.xpl.enum-no-factory-method", "enum没有定义工厂方法");

    ErrorCode ERR_XPL_UNKNOWN_FILTER_OP = define("nop.err.xlang.xpl.unknown-filter-op", "未定义的判别算子：{op}", ARG_OP);

    ErrorCode ERR_XPL_ATTR_NOT_VALID_CLASS_NAME = define("nop.err.xlang.xpl.attr-not-valid-class-name",
            "属性[{attrName}]的值[{attrValue}]不是合法的java类名", ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XPL_IMPORT_NOT_ALLOW_BOTH_FROM_AND_CLASS_ATTR = define(
            "nop.err.xlang.xpl.import-not-allow-both-lib-and-class-attr", "import标签不允许同时存在from和class属性");

    ErrorCode ERR_XPL_IMPORT_FROM_ADN_CLASS_BOTH_NULL = define("nop.err.xlang.xpl.import-from-and-class-both-null",
            "import标签的from或者class属性必须不为空");

    ErrorCode ERR_XPL_INVALID_LIB_NAMESPACE = define("nop.err.xlang.xpl.invalid-lib-namespace", "标签库文件名不是合法的名字空间",
            ARG_PATH);

    ErrorCode ERR_XPL_NOT_ALLOW_MULTIPLE_DECORATOR_CHILD = define("nop.err.xlang.xpl.only-allow-one-decorator-child",
            "不允许存在多个decorator子节点", ARG_NODE);

    ErrorCode ERR_XPL_DECORATOR_CHILD_NOT_ALLOW_TEXT_NODE = define(
            "nop.err.xlang.xpl.decorator-child-not-allow-text-node", "decorator不允许文本节点", ARG_NODE);

    ErrorCode ERR_XPL_DECORATOR_CHILD_NOT_ALLOW_MULTIPLE_DECORATED = define(
            "nop.err.xlang.xpl.decorator-child-not-allow-multiple-decorated", "decorator不允许多个decorated子节点",
            ARG_DECORATED);

    ErrorCode ERR_XPL_NOT_ALLOW_UNKNOWN_TAG = define("nop.err.xlang.xpl.not-allow-unknown-tag", "不允许使用未定义的标签:{tagName}",
            ARG_TAG_NAME, ARG_NODE);

    ErrorCode ERR_XPL_INVALID_LIB_PATH = define("nop.err.xlang.xpl.invalid-lib-path", "非法的标签库路径", ARG_PATH);

    ErrorCode ERR_XPL_INVALID_LIB_FILE_EXT = define("nop.err.xlang.xpl.invalid-lib-file-ext", "标签库文件的后缀名必须是xlib",
            ARG_PATH);

    ErrorCode ERR_XPL_UNKNOWN_SCRIPT_LANG = define("nop.err.xlang.xpl.unknown-script-lang",
            "未知的脚本语言:{lang},目前已注册的脚本语言有:{scriptLangs}", ARG_LANG, ARG_SCRIPT_LANGS);

    ErrorCode ERR_XPL_NOT_ALLOW_BOTH_RETURN_AND_OUTPUT_NODE = define(
            "nop.err.xlang.xpl.not-allow-both-return-and-output-node", "不允许同时输出XNode节点和返回XNode节点");

    ErrorCode ERR_XPL_RENDER_TAG_ONLY_ALLOW_IN_TAG_IMPL = define("nop.err.xlang.xpl.render-tag-only-in-tag-impl",
            "xpl:slot属性仅能在标签定义内部被使用");

    ErrorCode ERR_XPL_UNKNOWN_TAG_NAME = define("nop.err.xlang.xpl.unknown-tag-name", "未知的标签:{tagName}", ARG_TAG_NAME);

    ErrorCode ERR_XPL_NOT_CUSTOM_TAG_FUNC = define("nop.err.xlang.xpl.not-custom-tag-func", "不是自定义的函数标签:{tagName}",
            ARG_TAG_NAME);

    ErrorCode ERR_XPL_TAG_FUNC_TOO_MAY_ARGS = define("nop.err.xlang.xpl.tag-func-too-many-args",
            "标签函数[{tagName}]的参数个数过多，最多允许[{maxCount}]个参数", ARG_TAG_NAME, ARG_MAX_COUNT);

    // ErrorCode ERR_XPL_UNKNOWN_TAG_FRAME =
    // define("nop.err.xlang.xpl.unknown-frame",
    // "标签[{tagName}]没有定义xpl:frame:{frameName}", ARG_TAG_NAME, ARG_SLOT_NAME);

    ErrorCode ERR_XPL_TAG_NO_BODY = define("nop.err.xlang.xpl.tag-no-body", "标签[{tagName}]没有声明body参数", ARG_TAG_NAME,
            ARG_NODE);

    ErrorCode ERR_XPL_TAG_BODY_NOT_RENDERER = define("nop.err.xlang.xpl.tag-body-not-renderer",
            "标签[{tagName}]的body不是renderer类型", ARG_TAG_NAME, ARG_NODE);

    ErrorCode ERR_XPL_TAG_SLOT_NOT_RENDERER = define("nop.err.xlang.xpl.tag-body-not-renderer",
            "标签[{tagName}]的slot参数[{slotName}]不是renderer类型", ARG_TAG_NAME, ARG_SLOT_NAME, ARG_NODE);

    ErrorCode ERR_XPL_ATTRS_EXPR_VALUE_NOT_MAP = define("nop.err.xlang.xpl.attrs-expr-value-not-map",
            "xpl:attrs的值必须是Map类型:{className}", ARG_CLASS_NAME);

    ErrorCode ERR_EXEC_CALL_NULL_FUNCTION = define("nop.err.xlang.exec.call-null-function",
            "函数表达式返回null，无法调用函数: {funcExpr}", ARG_FUNC_EXPR);

    ErrorCode ERR_EXEC_GET_PROP_ON_NULL_OBJ = define("nop.err.xlang.exec.get-prop-on-null-obj",
            "对象表达式[{objExpr}]返回null，无法访问属性[{propName}]", ARG_OBJ_EXPR);

    ErrorCode ERR_EXEC_GET_ATTR_ON_NULL_OBJ = define("nop.err.xlang.exec.get-prop-on-null-obj",
            "对象表达式[{objExpr}]返回null，无法访问属性[{attrExpr}]", ARG_ATTR_EXPR, ARG_OBJ_EXPR);

    ErrorCode ERR_EXEC_CLASS_NOT_ALLOW_ATTR_EXPR = define("nop.err.xlang.exec.class-not-allow-attr-expr",
            "不允许在静态类上执行属性表达式:{className}", ARG_CLASS_NAME);

    ErrorCode ERR_EXEC_CLASS_NO_STATIC_FIELD = define("nop.err.xlang.exec.class-no-static-field",
            "类[{className}]上没有定义静态属性[{propName}]", ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_EXEC_CLASS_NO_STATIC_METHOD = define("nop.err.xlang.exec.class-no-static-method",
            "类[{className}]上没有定义静态方法[{methodName}]或者参数个数不匹配", ARG_CLASS_NAME, ARG_METHOD_NAME);

    ErrorCode ERR_EXEC_CLASS_NO_CONSTRUCTOR = define("nop.err.xlang.exec.class-no-constructor",
            "类[{className}]上没有定义匹配的构造函数", ARG_CLASS_NAME, ARG_ARG_COUNT);

    ErrorCode ERR_EXEC_ARRAY_NOT_SUPPORT_FUNCTION = define("nop.err.xlang.exec.array-not-allow-function",
            "数组类型不支持函数:{funcName}", ARG_FUNC_NAME);

    ErrorCode ERR_EXEC_OBJ_UNKNOWN_METHOD = define("nop.err.xlang.exec.obj-unknown-method",
            "类[{className}]上没有找到对应的方法定义[{methodName}]或者参数个数不匹配", ARG_CLASS_NAME, ARG_METHOD_NAME);

    ErrorCode ERR_EXEC_VALUE_NOT_ALLOW_NULL = define("nop.err.xlang.exec.value-not-allow-null", "值不允许为null");

    ErrorCode ERR_EXEC_VALUE_NOT_ALLOW_EMPTY = define("nop.err.xlang.exec.value-not-allow-empty", "值不允许为空");

    ErrorCode ERR_EXEC_READ_PROP_FAIL = define("nop.err.xlang.exec.read-prop-fail",
            "访问对象[{className}]的属性[{propName}]失败", ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_EXEC_WRITE_PROP_FAIL = define("nop.err.xlang.exec.write-prop-fail",
            "设置对象[{className}]的属性[{propName}]失败", ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_EXEC_READ_ATTR_FAIL = define("nop.err.xlang.exec.read-attr-fail",
            "访问对象[{className}]的属性[{attrValue}]失败", ARG_CLASS_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_EXEC_WRITE_ATTR_FAIL = define("nop.err.xlang.exec.write-attr-fail",
            "设置对象[{className}]的属性[{attrValue}]失败", ARG_CLASS_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_EXEC_READ_ATTR_NOT_STRING = define("nop.err.xlang.exec.read-attr-not-string",
            "对象[{className}]上不支持属性[{attrValue}]失败", ARG_CLASS_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_EXEC_OBJ_PROP_IS_NULL = define("nop.err.xlang.exec.obj-prop-is-null", "对象属性[{propName}]的值为null",
            ARG_PROP_NAME);

    ErrorCode ERR_EXEC_OBJ_ATTR_IS_NULL = define("nop.err.xlang.exec.obj-attr-is-null", "对象属性[{attrExpr}]的值为null",
            ARG_ATTR_EXPR);

    ErrorCode ERR_EXEC_READ_ATTR_EXPR_RETURN_NULL = define("nop.err.xlang.exec.read-attr-expr-return-null",
            "属性表达式[{attrExpr}]的返回值不能为null", ARG_ATTR_EXPR);

    ErrorCode ERR_EXEC_WRITE_ATTR_EXPR_RETURN_NULL = define("nop.err.xlang.exec.write-attr-expr-return-null",
            "属性表达式[{attrExpr}]的返回值不能为null", ARG_ATTR_EXPR);

    ErrorCode ERR_EXEC_UNKNOWN_STATIC_FIELD = define("nop.err.xlang.exec.unknown-static-field",
            "类[{className}]上没有定义静态属性[{propName}]", ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_EXEC_UNKNOWN_PROP = define("nop.err.xlang.exec.unknown-prop", "类[{className}]上没有定义属性[{propName}]",
            ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_EXEC_NOT_READABLE_PROP = define("nop.err.xlang.exec.not-readable-prop",
            "类[{className}]上的属性[{propName}]不是可读属性", ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_EXEC_NOT_WRITABLE_PROP = define("nop.err.xlang.exec.not-writable-prop",
            "类[{className}]上的属性[{propName}]不是可写属性", ARG_CLASS_NAME, ARG_PROP_NAME);

    ErrorCode ERR_EXEC_MAKE_PROP_OBJ_NULL = define("nop.err.xlang.exec.make-prop-obj-null", "makeProperty的对象为null");

    ErrorCode ERR_EXEC_WRITE_PROP_OBJ_NULL = define("nop.err.xlang.exec.write-prop-obj-null", "设置属性时对象不能为空");

    ErrorCode ERR_EXEC_MAKE_PROP_NULL = define("nop.err.xlang.exec.make-prop-null", "makeProperty的返回值不允许为空");

    ErrorCode ERR_EXEC_INVOKE_METHOD_FAIL = define("nop.err.xlang.exec.invoke-method-fail",
            "调用对象[{className}]上的方法[{funcName}]失败", ARG_CLASS_NAME, ARG_FUNC_NAME);

    ErrorCode ERR_EXEC_INVOKE_FUNCTION_FAIL = define("nop.err.xlang.exec.invoke-function-fail", "调用函数失败");

    ErrorCode ERR_EXEC_NO_OBJ_METHOD = define("nop.err.xlang.exec.no-obj-method",
            "类型为[{className}]的对象上没有定义方法[{funcName}({argCount})]", ARG_CLASS_NAME, ARG_FUNC_NAME, ARG_ARG_COUNT);

    ErrorCode ERR_EXEC_EXPR_NOT_RETURN_FUNC = define("nop.err.xlang.exec.expr-not-return-func",
            "表达式的返回结果不是函数类型:{funcExpr}", ARG_FUNC_EXPR);

    ErrorCode ERR_EXEC_CONVERT_FUNC_ONLY_ALLOW_ONE_ARG = define("nop.err.xlang.exec.convert-func-only-allow-one-arg",
            "类型转换函数[{funcName}]只允许一个参数", ARG_FUNC_NAME);

    ErrorCode ERR_EXEC_FOR_IN_ITEMS_MUST_BE_MAP = define("nop.err.xlang.exec.for-in-items-must-be-map",
            "for-in语句的items表达式必须返回Map类型的对象");

    ErrorCode ERR_EXEC_NOT_SUPPORTED_OPERATOR = define("nop.err.xlang.exec.not-supported-operator", "不支持的运算符:{op}",
            ARG_OP);

    ErrorCode ERR_EXEC_THROW_INVALID_ERROR = define("nop.err.xlang.exec.throw-invalid-error",
            "throw语句抛出的异常对象只能是异常码或者Throwable类型", ARG_ERROR);

    ErrorCode ERR_EXEC_XML_EXT_ATTRS_NOT_MAP = define("nop.err.xlang.exec.xml-ext-attrs-not-map",
            "attrs表达式的返回值不是Map类型");

    ErrorCode ERR_EXEC_LOOP_STEP_MUST_NOT_BE_ZERO = define("nop.err.xlang.exec.loop-step-must-not-be-zero",
            "循环的步长参数不能为0");

    ErrorCode ERR_EXEC_CALL_FUNC_FAIL = define("nop.err.xlang.exec.call-func-fail", "调用函数失败");

    ErrorCode ERR_EXEC_LOG_MESSAGE_NOT_STATIC = define("nop.err.xlang.exec.log-message-not-static",
            "log函数的第一个参数只能是静态字符串，不能是动态拼接的字符串", ARG_EXPR);

    ErrorCode ERR_EXEC_LOG_MESSAGE_NOT_ALLOW_EMPTY = define("nop.err.xlang.exec.log-message-not-static",
            "log函数的第一个参数为消息模板，它不能为空", ARG_EXPR);

    ErrorCode ERR_EXEC_TOO_MANY_ARGS = define("nop.err.xlang.exec.too-many-args", "参数个数超过限制：maxArgCount={maxCount}",
            ARG_MAX_COUNT);

    ErrorCode ERR_EXEC_TOO_FEW_ARGS = define("nop.err.xlang.exec.too-few-args", "参数个数不足：minArgCount={minCount}",
            ARG_MIN_COUNT);

    ErrorCode ERR_EXEC_INVALID_ARG_COUNT = define("nop.err.xlang.exec.invalid-arg-count", "参数个数不正确：expected={expected}",
            ARG_EXPECTED);

    ErrorCode ERR_EXEC_THROW_NULL_EXCEPTION = define("nop.err.xlang.exec.throw-null-exception", "throw语句的参数为null");

    ErrorCode ERR_EXEC_THROW_EXCEPTION = define("nop.err.xlang.exec.throw-exception", "抛出异常");

    ErrorCode ERR_EXEC_EXPR_NOT_THROWABLE = define("nop.err.xlang.exec.arg-not-throwable",
            "表达式的执行结果不是Throwable类型:{expr}", ARG_EXPR);

    ErrorCode ERR_EXEC_SPREAD_ITEM_NOT_MAP = define("nop.err.xlang.exec.spread-item-not-map",
            "扩展表达式的返回值[{value}]不是Map类型", ARG_VALUE);

    ErrorCode ERR_EXEC_ARRAY_BINDING_NOT_LIST = define("nop.err.xlang.exec.array-binding-not-list",
            "ArrayBinding表达式的返回值[{value}]不是List类型", ARG_VALUE);

    ErrorCode ERR_EXEC_OBJECT_BINDING_NOT_MAP = define("nop.err.xlang.exec.object-binding-not-map",
            "ObjectBinding表达式的返回值[{value}]不是Map类型", ARG_VALUE);

    ErrorCode ERR_EXEC_NOT_SUPPORTED_AST_NODE = define("nop.err.xlang.exec.not-supported-node", "不支持的语法节点",
            ARG_AST_NODE);

    ErrorCode ERR_EXEC_IDENTIFIER_NOT_INITIALIZED = define("nop.err.xlang.exec.identifier-not-initialized",
            "变量[{varName}]没有初始化", ARG_VAR_NAME);

    ErrorCode ERR_EXEC_SCOPE_VAR_IS_UNDEFINED = define("nop.err.xlang.exec.scope-var-is-undefined",
            "scope变量[{varName}]没有定义", ARG_VAR_NAME);

    ErrorCode ERR_EXEC_PROGRAM_SHOULD_NOT_USE_EXTERNAL_CLOSURE_VAR = define(
            "nop.err.xlang.exec.program-should-not-use-external-closure-var", "Program不应该使用外部闭包变量:{varName}",
            ARG_VAR_NAME);

    ErrorCode ERR_EXEC_COLLECT_RESULT_NOT_SINGLE_NODE = define("nop.err.xlang.exec.collect-result-not-single-node",
            "c:collect标签设置了singleMode模式，但是具体执行得到的结果节点不是单节点");

    ErrorCode ERR_EXEC_NOT_LITERAL_VALUE = define("nop.err.xlang.exec.not-literal-value", "表达式不是字面量，无法获取到静态值",
            ARG_EXPR);

    ErrorCode ERR_EXEC_INJECT_PARAM_NOT_NAME_OR_TYPE = define("nop.err.xlang.exec.inject-param-not-name-or-type",
            "inject函数的参数必须是字符串或者Class类型");

    ErrorCode ERR_XDSL_PROP_VALUE_NOT_LIST = define("nop.err.xlang.xdsl.prop-value-not-list",
            "属性[{propName}]的值[{value}]不是列表类型", ARG_PROP_NAME, ARG_VALUE);

    ErrorCode ERR_XDSL_PROP_VALUE_NOT_MAP = define("nop.err.xlang.xdsl.prop-value-not-map",
            "属性[{propName}]的值[{value}]不是Map类型", ARG_PROP_NAME, ARG_VALUE);

    ErrorCode ERR_XDSL_PROP_LIST_ITEM_NOT_MAP = define("nop.err.xlang.xdsl.prop-list-item-not-map",
            "属性[{propName}]的值应为对象结构，不允许空值", ARG_PROP_NAME);

    ErrorCode ERR_XDSL_SUB_TYPE_PROP_VALUE_NOT_STRING = define("nop.err.xlang.xdsl.sub-type-prop-value-not-string",
            "属性[{propName}]的类型为union，它必须具有文本类型的、非空的子类型属性[{subTypeProp}]", ARG_PROP_NAME, ARG_SUB_TYPE_PROP);

    ErrorCode ERR_XDSL_SUB_TYPE_PROP_IS_EMPTY = define("nop.err.xlang.xdsl.sub-type-prop-is-empty",
            "子类型属性[{subTypeProp}]的值不能为空", ARG_SUB_TYPE_PROP);

    ErrorCode ERR_XDSL_PROP_NO_SUB_SCHEMA_DEFINITION = define("nop.err.xlang.xdsl.prop-no-sub-schema-definition",
            "属性[{propName}]的类型为union，但是没有找到针对子类型[{subTypeValue}]的子类型定义", ARG_PROP_NAME, ARG_SUB_TYPE_VALUE);

    ErrorCode ERR_XDSL_NO_SUB_SCHEMA_DEFINITION = define("nop.err.xlang.xdsl.no-sub-schema-definition",
            "没有找到针对子类型[{subTypeValue}]的子对象定义", ARG_SUB_TYPE_VALUE);

    ErrorCode ERR_XDSL_UNKNOWN_PROP = define("nop.err.xlang.xdsl.unknown-prop", "未知属性:{propName}", ARG_PROP_NAME);

    ErrorCode ERR_XDSL_NOT_SUPPORTED_SCHEMA_KIND = define("nop.err.xlang.xdsl.not-supported-schema-kind",
            "属性[{propName}]不支持schema类型[{schemaKind}]", ARG_PROP_NAME, ARG_SCHEMA_KIND);

    ErrorCode ERR_XDSL_INVALID_OVERRIDE_ATTR = define("nop.err.xlang.xdsl.invalid-override-attr",
            "override属性的值不在XDefOverride常量类的定义范围内", ARG_OVERRIDE);

    ErrorCode ERR_XDSL_NOT_ALLOW_MERGE_BETWEEN_NODE = define("nop.err.xlang.xdsl.not-allow-merge-between-node",
            "不允许两个节点之间定义合并操作，它们的合并算子不兼容", ARG_NODE_A, ARG_NODE_B);

    ErrorCode ERR_XDSL_FINAL_NODE_NOT_ALLOW_OVERRIDE = define("nop.err.xlang.xdsl.final-node-not-allow-override",
            "已经标记为final的节点不允许被覆盖", ARG_NODE);

    ErrorCode ERR_XDSL_NOT_REQUIRED_SCHEMA = define("nop.err.xlang.xdsl.not-required-schema",
            "x:schema属性所指定的schema[{schemaPath}]不是期待的[{requiredSchema}]", ARG_SCHEMA_PATH, ARG_REQUIRED_SCHEMA);

    ErrorCode ERR_XDSL_ATTR_VALUE_IS_EMPTY = define("nop.err.xlang.xdsl.attr-value-is-empty",
            "[{tagName}]节点的属性[{attrName}]不允许为空", ARG_TAG_NAME, ARG_NODE, ARG_ATTR_NAME);

    ErrorCode ERR_XDSL_MULTIPLE_NODE_HAS_SAME_UNIQUE_ATTR_VALUE = define(
            "nop.err.xlang.xdsl.multiple-node-has-same-unique-attr-value", "多个节点具有同样的唯一属性值:{attrName}={attrValue}",
            ARG_NODE_A, ARG_NODE_B, ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XDSL_NODE_UNIQUE_KEY_VALUE_NOT_ALLOW_EMPTY = define(
            "nop.err.xlang.xdsl.node-unique-key-value-not-allow-empty", "节点[{node}]的唯一键[{attrName}]不允许为空", ARG_NODE,
            ARG_ATTR_NAME);

    ErrorCode ERR_XDSL_NOT_ALLOW_MULTIPLE_SUPER = define("nop.err.xlang.xdsl.not-allow-multiple-super",
            "不允许存在多个x:super子节点", ARG_NODE);

    ErrorCode ERR_XDSL_NOT_FIND_PROTOTYPE_NODE = define("nop.err.xlang.xdsl.not-find-prototype-node",
            "x:prototype属性所对应的节点不存在:{value}", ARG_VALUE);

    ErrorCode ERR_XDSL_NOT_FIND_SUPER_NODE = define("nop.err.xlang.xdsl.not-find-super-node",
            "没有发现x:super子节点。merge-super和replace-super算子要求必须定义x:super子节点。", ARG_NODE);

    ErrorCode ERR_XDSL_SUPER_NODE_NOT_ALLOW_BODY = define("nop.err.xlang.xdsl.super-node-not-allow-body",
            "x:super子节点不允许存在内容", ARG_NODE);

    ErrorCode ERR_XDSL_MULTIPLE_NODE_WITH_SAME_TAG = define("nop.err.xlang.xdsl.multiple-node-with-same-tag",
            "多个节点具有同样的标签名", ARG_NODE, ARG_TAG_NAME);

    ErrorCode ERR_XDSL_UNDEFINED_CHILD_NODE = define("nop.err.xlang.xdsl.undefined-child-node",
            "在xdef模型[{xdefNodeName}]中未定义此节点:{tagName}", ARG_NODE, ARG_XDEF_NODE_NAME, ARG_TAG_NAME);

    ErrorCode ERR_XDSL_MISSING_MANDATORY_CHILD = define("nop.err.xlang.xdsl.missing-mandatory-child",
            "缺少必须的子节点[{tagName}]", ARG_TAG_NAME);

    ErrorCode ERR_XDSL_SUPER_EXTENDS_NO_CURRENT_PATH = define("nop.err.xlang.xdsl.super-extends-no-current-path",
            "当前节点没有资源路径，无法确定x:extends=super所对应的模型文件");

    ErrorCode ERR_XDSL_SUPER_EXTENDS_INVALID_PATH = define("nop.err.xlang.xdsl.super-extends-invalid-path",
            "当前节点路径不是虚拟文件路径，无法使用x:extends=super配置");

    ErrorCode ERR_XDSL_RUN_EXTENDS_RESULT_NOT_NODE = define("nop.err.xlang.xdsl.run-extends-result-not-node",
            "执行x:extends的输出结果不是XNode类型");

    ErrorCode ERR_XDSL_ATTR_NOT_VALID_CLASS_NAME = define("nop.err.xlang.xdsl.attr-not-valid-class-name",
            "属性[{attrName}]的值[{attrValue}]不是合法的java类名", ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XDSL_ATTR_NOT_VALID_V_PATH = define("nop.err.xlang.xdsl.attr-not-valid-v-path",
            "属性[{attrName}]的值[{attrValue}]不是合法的虚拟文件路径", ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XDSL_ATTR_NOT_VALID_PROP_NAME = define("nop.err.xlang.xdsl.attr-not-valid-prop-name",
            "属性[{attrName}]的值[{attrValue}]不是合法的java属性名", ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XDSL_ATTR_NOT_VALID_XML_NAME = define("nop.err.xlang.xdsl.attr-not-valid-xml-name",
            "属性[{attrName}]的值[{attrValue}]不是合法的XML属性名或标签名", ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XDSL_ATTR_NOT_VALID_BOOLEAN = define("nop.err.xlang.xdsl.attr-not-valid-boolean",
            "属性[{attrName}]的值[{attrValue}]不是合法的布尔值,允许值true/false或者1/0", ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XDSL_ATTR_NOT_VALID_ENUM_VALUE = define("nop.err.xlang.xdsl.attr-not-valid-enum-value",
            "属性[{attrName}]的值[{attrValue}]不是合法的枚举值", ARG_ATTR_NAME, ARG_ATTR_VALUE, ARG_ENUM_CLASS);

    ErrorCode ERR_XDSL_ATTR_NOT_VALID_LOCAL_REF = define("nop.err.xlang.xdsl.attr-not-valid-local-ref",
            "属性[{attrName}]的值[{attrValue}]不是合法的引用名称，它必须以@为前缀", ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XDSL_ATTR_NOT_VALID_GENERIC_TYPE = define("nop.err.xlang.xdsl.attr-not-valid-generic-type",
            "属性[{attrName}]的值[{attrValue}]不是合法的类型定义", ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XDSL_ATTR_NOT_VALID_DEF_TYPE = define("nop.err.xlang.xdsl.attr-not-valid-def-type",
            "属性[{attrName}]的值[{attrValue}]不是合法的xdef类型定义", ARG_ATTR_NAME, ARG_ATTR_VALUE);

    ErrorCode ERR_XDSL_VALUE_NOT_VALID_DEF_TYPE = define("nop.err.xlang.xdsl.value-not-valid-def-type",
            "值[{value}]不是合法的xdef类型定义", ARG_VALUE);

    ErrorCode ERR_XDSL_ATTR_NOT_ALLOWED = define("nop.err.xlang.xdsl.attr-not-allowed",
            "不允许的属性:{attrName}, 允许的名称为:{allowedNames}", ARG_ATTR_NAME, ARG_ALLOWED_NAMES);

    ErrorCode ERR_XDSL_DELTA_NODE_NO_INHERIT = define("nop.err.xlang.xdsl.delta-node-not-inherit",
            "节点没有参与继承合并操作：{node}", ARG_NODE);

    ErrorCode ERR_XDSL_TAG_NAME_NOT_ALLOWED = define("nop.err.xlang.xdsl.tag-name-not-allowed",
            "不允许的节点名称:{tagName}],允许的名称为:{allowedNames}", ARG_TAG_NAME, ARG_ALLOWED_NAMES);

    ErrorCode ERR_XDSL_NODE_NOT_ALLOW_CONTENT = define("nop.err.xlang.xdsl.node-not-allow-content", "节点不允许具有文本内容");

    ErrorCode ERR_XDSL_NODE_CONTENT_NOT_ALLOW_EMPTY = define("nop.err.xlang.xdsl.node-content-not-allow-empty",
            "节点内容不允许为空", ARG_NODE);

    ErrorCode ERR_XDSL_NODE_DUPLICATE_CHILD = define("nop.err.xlang.xdsl.node-duplicate-child", "节点的子节点名重复：{tagName}",
            ARG_TAG_NAME);

    ErrorCode ERR_XDSL_NODE_DUPLICATE_CHILD_FOR_MAP = define("nop.err.xlang.xdsl.node-duplicate-child-for-map",
            "Map节点的子节点名重复：{tagName}", ARG_TAG_NAME);

    ErrorCode ERR_XDSL_NO_SCHEMA = define("nop.err.xlang.xdsl.no-schema", "必须通过x:schema属性来指定元模型");

    ErrorCode ERR_XDSL_CONFIG_CHILD_MUST_BE_IMPORT = define("nop.err.xlang.xdsl.config-child-must-be-import",
            "x:config节点的内容必须是c:import标签，不允许其他标签");

    ErrorCode ERR_XDSL_NODE_UNEXPECTED_TAG_NAME = define("nop.err.xlang.xdsl.node-unexpected-tag-name",
            "节点名称为[{tagName}],期望的节点名称为[{expected}]", ARG_TAG_NAME, ARG_EXPECTED);

    ErrorCode ERR_XDSL_MODEL_NO_NAME_ATTR =
            define("nop.err.xlang.xdsl.model-no-name-attr", "模型对象没有name属性");

    ErrorCode ERR_XDSL_UNDEFINED_META_CFG_VAR =
            define("nop.err.xlang.xdsl.undefined-meta-cfg-var", "没有定义配置变量：{configVars}", ARG_CONFIG_VARS);

    ErrorCode ERR_XDSL_ATTR_NOT_BEAN_PROP =
            define("nop.err.xlang.xdsl.attr-not-bean-prop", "属性[{attrName}]不是类[{className}]的属性", ARG_ATTR_NAME, ARG_CLASS_NAME);

    ErrorCode ERR_XDEF_DUPLICATE_LOCAL_REF = define("nop.err.xlang.xdef.duplicate-local-ref", "xdef:name引用名称不能重复",
            ARG_REF_NAME);

    ErrorCode ERR_XDEF_DUPLICATE_NODE_ID = define("nop.err.xlang.xdef.duplicate-node-id", "xdef:id名称不能重复", ARG_ID);

    ErrorCode ERR_XDEF_DUPLICATE_CHILD = define("nop.err.xlang.xdef.duplicate-child", "子节点名称不能重复", ARG_TAG_NAME);

    ErrorCode ERR_XDEF_TAG_NAME_CONFLICT_WITH_ATTR_NAME = define("nop.err.xlang.xdef.tag-name-conflict-with-attr-name",
            "子节点名称不能与属性名重复:{tagName}", ARG_TAG_NAME);

    ErrorCode ERR_XDEF_REF_ONLY_ALLOW_ON_OBJ_NODE = define("nop.err.xlang.xdef.ref-only-allow-on-obj-node",
            "只有对象节点允许设置xdef:ref属性。body-type不为空的节点可能映射到List/Map等结构，而不是对象类", ARG_NODE);

    ErrorCode ERR_XDEF_REF_NOT_ALLOW_CIRCULAR_REFERENCE = define("nop.err.xlang.xdef.ref-not-allow-circular-reference",
            "xdef:ref不允许循环引用:{refName}", ARG_REF_NAME);

    ErrorCode ERR_XDEF_UNKNOWN_DEFINITION_REF = define("nop.err.xlang.xdef.unknown-definition-ref",
            "xdef:ref定义未找到:{refName}", ARG_REF_NAME);

    ErrorCode ERR_XDEF_INTERNAL_REF_NODE_NOT_ALLOW_ATTRS = define(
            "nop.err.xlang.xdef.internal-ref-node-not-allow-attrs", "xdef:ref为内部引用时，该节点不允许设置其他属性:{node}", ARG_NODE);

    ErrorCode ERR_XDEF_VALUE_AND_CONTENT_NOT_ALLOW_BOTH = define(
            "nop.err.xlang.xdef.value-and-content-not-allow-both", "xdef:value和节点内容表示同样的内容，不能同时设置", ARG_NODE);

    ErrorCode ERR_XDEF_ATTR_NOT_ALLOW_OVERRIDE_REF = define("nop.err.xlang.xdef.attr-not-allow-override-ref",
            "属性[{attrName}]已经存在，不允许覆盖", ARG_ATTR_NAME);

    ErrorCode ERR_XDEF_PROP_NOT_ALLOW_OVERRIDE = define("nop.err.xlang.xdef.prop-not-allow-override",
            "属性[{propName}]已经存在，不允许覆盖", ARG_PROP_NAME);

    ErrorCode ERR_XDEF_CHILD_NOT_ALLOW_OVERRIDE_REF = define("nop.err.xlang.xdef.child-not-allow-override-ref",
            "子节点[{tagName}]已经存在，不允许覆盖", ARG_TAG_NAME);

    ErrorCode ERR_XDEF_LIST_NO_CHILD = define("nop.err.xlang.xdef.list-no-child", "列表类型的节点没有定义元素类型");

    ErrorCode ERR_XDEF_UNION_ELEMENT_TYPE_IS_UNKNOWN = define("nop.err.xlang.xdef.union-element-type-is-unknown",
            "union类型的元素必须具有确定的类型", ARG_NODE);

    ErrorCode ERR_XDEF_SET_NO_CHILD = define("nop.err.xlang.xdef.set-no-child", "集合类型的节点没有定义元素类型");

    ErrorCode ERR_XDEF_MAP_NO_CHILD = define("nop.err.xlang.xdef.map-no-child", "Map类型的节点没有定义元素类型");

    ErrorCode ERR_XDEF_KEYED_LIST_MUST_ASSIGN_BEAN_BODY_TYPE_EXPLICITLY = define(
            "nop.err.xlang.xdef.keyed-list-must-assign-bean-body-type-explicitly",
            "节点对应KeyedList类型且具有多个子节点时必须明确指定xdef:bean-body-type属性");

    ErrorCode ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_XML_CHILD = define("nop.err.xlang.xdef.std-domain-not-support-xml-child",
            "[stdDomain]格式不支持XML子节点", ARG_STD_DOMAIN);

    ErrorCode ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN = define(
            "nop.err.xlang.xdef.illegal-prop-value-for-std-domain", "属性[{propName}]的值[{value}]不满足[{stdDomain}]格式要求",
            ARG_PROP_NAME, ARG_STD_DOMAIN, ARG_VALUE);

    ErrorCode ERR_XDEF_ILLEGAL_CONTENT_VALUE_FOR_STD_DOMAIN = define(
            "nop.err.xlang.xdef.illegal-content-value-for-std-domain", "节点[{tagName}]的值[{value}]不满足[{stdDomain}]格式要求",
            ARG_NODE, ARG_TAG_NAME, ARG_STD_DOMAIN, ARG_VALUE);

    ErrorCode ERR_XDEF_ILLEGAL_BODY_VALUE_FOR_STD_DOMAIN = define(
            "nop.err.xlang.xdef.illegal-body-value-for-std-domain", "节点[{tagName}]的内容不满足[{stdDomain}]格式要求", ARG_NODE,
            ARG_TAG_NAME, ARG_STD_DOMAIN);
    ErrorCode ERR_XDEF_ILLEGAL_CLASS_NAME_FOR_ENUM_DOMAIN = define(
            "nop.err.xlang.xdef.illegal-class-name-for-enum-domain", "[className]不是合法的java类名", ARG_CLASS_NAME);

    ErrorCode ERR_XDEF_INVALID_ENUM_VALUE_FOR_PROP = define("nop.err.xlang.xdef.invalid-enum-value",
            "属性[{propName}]的值[{value}]不是合法的[{dictName}]枚举值", ARG_PROP_NAME, ARG_VALUE, ARG_DICT_NAME, ARG_ALLOWED_VALUES);

    ErrorCode ERR_XDEF_ENUM_VALUE_NOT_IN_OPTIONS =
            define("nop.err.xlang.xdef.enum-value-not-in-options", "属性[{propName}]的值[{value}]不在选项列表[{options}]中");

    ErrorCode ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_OPTIONS = define("nop.err.xlang.xdef.std-domain-not-support-options",
            "[{stdDomain}]不支持扩展选项", ARG_STD_DOMAIN);

    ErrorCode ERR_XDEF_FN_NO_TYPE_DECL = define("nop.err.xlang.xdef.fn-no-type-decl",
            "[{stdDomain}]缺少函数类型声明", ARG_STD_DOMAIN);


    ErrorCode ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_PROP = define("nop.err.xlang.xdef.std-domain-not-support-prop",
            "[{stdDomain}]不支持属性设置，只支持XML节点配置", ARG_STD_DOMAIN);

    ErrorCode ERR_XDEF_STD_DOMAIN_NOT_ALLOW_NODE_CONTENT = define(
            "nop.err.xlang.xdef.std-domain-not-allow-node-content", "[{stdDomain}]不支持节点文本，只支持XML子节点配置", ARG_STD_DOMAIN);

    ErrorCode ERR_XDEF_SET_NODE_MUST_HAS_KEY_ATTR = define("nop.err.xlang.xdef.set-node-must-has-key-attr",
            "标记为xdef:body-type=set的集合节点必须具有xdef:key-attr属性", ARG_NODE);

    ErrorCode ERR_XDEF_SET_NODE_CHILD_NO_ATTR = define("nop.err.xlang.xdef.set-node-child-no-attr",
            "集合节点的子节点缺少指定的属性[{attrName}]", ARG_ATTR_NAME, ARG_NODE);

    ErrorCode ERR_XDEF_LIST_NODE_NOT_ALLOW_VALUE = define("nop.err.xlang.xdef.list-node-not-allow-value",
            "集合节点不允许设置xdef:value");

    ErrorCode ERR_XDEF_MAP_NODE_NOT_ALLOW_VALUE = define("nop.err.xlang.xdef.map-node-not-allow-value",
            "Map节点不允许设置xdef:value");

    ErrorCode ERR_XDEF_SIMPLE_NODE_NOT_ALLOW_ATTR = define("nop.err.xlang.xdef.simple-node-not-allow-attr",
            "简单节点不允许包含属性", ARG_ATTR_NAME, ARG_NODE);

    ErrorCode ERR_XDEF_DUPLICATE_BEAN_CLASS = define("nop.err.xlang.duplicate-bean-class",
            "节点的beanClass定义重复:{beanClass}", ARG_NODE, ARG_BEAN_CLASS);

    ErrorCode ERR_XDEF_ANY_TAG_NODE_NO_BEAN_CLASS_ATTR = define("nop.err.xlang.xdef.node-no-bean-class-attr",
            "对象节点需要x:bean-class属性来指定对象类名");

    ErrorCode ERR_XDEF_UNIQUE_ATTR_NOT_ALLOW_ON_XDEF_ANY_TAG = define(
            "nop.err.xlang.xdef.unique-attr-not-allow-on-xdef-any-tag", "xdef:unique-attr属性不允许标注在xdef:any-tag标签上");

    ErrorCode ERR_XDEF_UNIQUE_ATTR_VALUE_MUST_BE_NODE_ATTR = define(
            "nop.err.xlang.xdef.unique-attr-value-must-be-node-attr", "xdef:unique-attr指定的名称[{attrName}]必须是节点的属性名",
            ARG_ATTR_NAME);

    ErrorCode ERR_XDEF_UNKNOWN_STD_DOMAIN = define("nop.err.xlang.xdef.unknown-std-domain", "未定义的stdDomain:{stdDomain}",
            ARG_STD_DOMAIN);

    ErrorCode ERR_XDEF_CHILD_NOT_SUPPORT_EXTENDS = define("nop.err.xlang.xdef.child-not-support-extends",
            "本节点不支持x:extends设置:{node}，参见它的XDefNode定义: {xdefPath}", ARG_XDEF_PATH, ARG_NODE);

    ErrorCode ERR_XDEF_NO_BEAN_CLASS_ATTR = define("nop.err.xlang.xdef.no-bean-class-attr",
            "xdef定义没有指定xdef:bean-class属性，不支持创建DslModel", ARG_NODE);

    ErrorCode ERR_JAVAC_PARSE_FAIL = define("nop.err.xlang.javac.parse-fail", "解析java文件失败");

    ErrorCode ERR_XMETA_SCHEMEA_DEFINE_NO_NAME_ATTR = define("nop.err.xlang.xmeta.schema-define-no-name-attr",
            "schema定义没有name属性");

    ErrorCode ERR_XMETA_UNKNOWN_TYPE = define("nop.err.xlang.xmeta.unknown-type", "未定义的类型:{typeName}", ARG_TYPE_NAME);

    ErrorCode ERR_XMETA_UNION_SCHEMA_NO_SUB_TYPE_PROP = define("nop.err.xlang.xmeta.union-schema-no-sub-type-prop",
            "union schema的子节点必须具有xdef:bean-tag-prop属性，通过标签名来实现类型区分。" + "或者在union节点上增加xdef:bean-sub-type-prop配置");

    ErrorCode ERR_XMETA_UNKNOWN_REF = define("nop.err.xlang.xmeta.unknown-ref", "未知的引用定义:{refName}", ARG_REF_NAME);

    ErrorCode ERR_XMETA_ONLY_ALLOW_ONE_TOP_CLASS = define("nop.err.xlang.xmeta.only-allow-one-top-class",
            "每个元数据定义文件中仅允许一个顶层类");

    ErrorCode ERR_XMETA_PACKAGE_MEMBER_MUST_BE_CLASS = define("nop.err.xlang.xmeta.package-member-must-be-class",
            "元数据定义文件中只允许java类定义，不允许引入接口或者enum");

    ErrorCode ERR_XMETA_INVALID_PROP_META_PROP_VALUE = define("nop.err.xlang.xmeta.invalid-prop-meta-prop",
            "propMeta的属性[{propName}]的值[{value}]格式不正确", ARG_PROP_NAME, ARG_VALUE);

    ErrorCode ERR_JAVA_UNSUPPORTED_ELEMENT_VALUE = define("nop.err.xlang.java.unsupported-element-value",
            "不支持的值类型:{value}", ARG_VALUE);

    ErrorCode ERR_JAVA_NOT_SUPPORT_CLASS_TYPE_PARAMETER = define("nop.err.xlang.java.not-support-class-type-parameter",
            "不支持定义泛型类:{className}", ARG_CLASS_NAME);

    ErrorCode ERR_JAVA_TYPE_ALIAS_CONFLICTED = define("nop.err.xlang.java.type-alias-conflicted",
            "类型名称与导入的类名或者本文件中定义的其他类名冲突:{typeName}", ARG_TYPE_NAME);

    ErrorCode ERR_XLANG_XPL_EXPR_NOT_ALLOW_NESTED = define("nop.err.xlang.xpl-expr-not-allow-nested",
            "标签表达式的参数只能是普通表达式，而不允许是标签表达式");

    ErrorCode ERR_XLANG_XPL_EXPR_BRACKET_NOT_MATCH = define("nop.err.xlang.xpl-expr-bracket-not-match", "#[和]必须一一对应");

    ErrorCode ERR_XLANG_XPL_EXPR_PAREN_NOT_MATCH = define("nop.err.xlang.xpl-expr-paren-not-match", "(和)必须一一对应");

    ErrorCode ERR_XLANG_XPL_EXPR_INVALID_MODE = define("nop.err.xlang.xpl-expr-invalid-mode", "Lexer状态不正确");

    ErrorCode ERR_XLANG_DEBUGGER_ALREADY_CLOSED = define("nop.err.xlang.debugger-already-closed", "调试器已关闭");

    ErrorCode ERR_XLIB_TAG_NO_SOURCE_DEFINED = define("nop.err.xpl.xlib.tag-no-source-defined",
            "标签库没有定义内嵌的source实现，也没有外部关联的[{resourceName}]实现文件", ARG_RESOURCE_NAME);

    ErrorCode ERR_XLIB_NODE_SLOT_NOT_SUPPORT_ARG = define("nop.err.xpl.xlib.node-slot-not-support-arg",
            "bodyType为node的slot不支持arg配置", ARG_NODE);

    ErrorCode ERR_XLIB_SLOT_NAME_CONFLICT_WITH_ATTR_NAME = define("nop.err.xpl.xlib.slot-name-conflict-with-attr-name",
            "slot的变量名[slotName]与标签属性名[attrName]冲突", ARG_SLOT_NAME, ARG_ATTR_NAME);

    ErrorCode ERR_XLIB_NOT_ALLOW_NAMED_SLOT_IF_DEFAULT_IS_USED = define(
            "nop.err.xpl.xlib.not-allow-named-slot-if-default-is-used", "如果定义了名为default的slot，则不能再定义其他slot");

    ErrorCode ERR_XLIB_UNKNOWN_TAG = define("nop.err.xpl.xlib.unknown-tag", "在标签库[{libPath}]中没有定义标签[{tagName}]",
            ARG_LIB_PATH, ARG_TAG_NAME);

    ErrorCode ERR_LAYOUT_UNSUPPORTED_LAYER_NUMBER = define("nop.err.layout.unknown-layer-number", "最多只支持#####五层结构");

    ErrorCode ERR_LAYOUT_INVALID_LINE = define("nop.err.layout.invalid-line", "行描述不符合要求");

    ErrorCode ERR_XPL_DISALLOW_OUTPUT_INVALID_XML_NAME = define("nop.err.xpl.invalid-xml-name", "不允许输出非法的XML名称:{}",
            ARG_NAME);

    ErrorCode ERR_SCHEMA_PROP_CONVERT_TO_TYPE_FAIL =
            define("nop.err.schema.prop-convert-to-type-fail",
                    "属性[{propName}]的值[{value}]不能转换为类型[{targetType}]", ARG_PROP_NAME,
                    ARG_TARGET_TYPE, ARG_VALUE);

    ErrorCode ERR_SCHEMA_PROP_STD_DOMAIN_VALIDATION_FAIL =
            define("nop.err.schema.std-domain-validation-fail",
                    "属性[{propName}]的值[{value}]不满足[{stdDomain}]格式要求");

    ErrorCode ERR_OBJ_SCHEMA_NO_PROP =
            define("nop.err.schema.obj-schema-no-prop", "对象没有定义名称为[{propName}]的属性", ARG_PROP_NAME);

    ErrorCode ERR_SCHEMA_PROP_NOT_MATCH_PATTERN =
            define("nop.err.schema.prop-not-match-pattern", "属性[{propName}]的值不满足格式要求",
                    ARG_PROP_NAME, ARG_PATTERN);

    ErrorCode ERR_SCHEMA_PROP_VALUE_TOO_SMALL =
            define("nop.err.schema.prop-value-too-small",
                    "属性[{propName}]的值[{value}]小于最小值[{minValue}]", ARG_PROP_NAME, ARG_VALUE, ARG_MIN_VALUE);

    ErrorCode ERR_SCHEMA_PROP_VALUE_TOO_LARGE =
            define("nop.err.schema.prop-value-too-large",
                    "属性[{propName}]的值[{value}]大于最小值[{minValue}]", ARG_PROP_NAME, ARG_VALUE, ARG_MAX_VALUE);

    ErrorCode ERR_SCHEMA_PROP_LENGTH_GREATER_THAN_MAX_LENGTH =
            define("nop.err.schema.prop-length-greater-than-max-length",
                    "属性[{propName}]的值[{value}]的长度超过最大值[{maxLength}]", ARG_PROP_NAME, ARG_VALUE, ARG_MAX_LENGTH);

    ErrorCode ERR_SCHEMA_PROP_LENGTH_GREATER_THAN_UTF8_LENGTH =
            define("nop.err.schema.prop-length-greater-than-utf8-length",
                    "属性[{propName}]的值[{value}]的UTF8长度超过最大值[{maxLength}]", ARG_PROP_NAME, ARG_VALUE, ARG_MAX_LENGTH);

    ErrorCode ERR_SCHEMA_PROP_LENGTH_LESS_THAN_MIN_LENGTH =
            define("nop.err.schema.prop-length-less-than-min-length",
                    "属性[{propName}]的值[{value}]的长度小于最小值[{maxLength}]", ARG_PROP_NAME, ARG_VALUE, ARG_MIN_LENGTH);

    ErrorCode ERR_XLANG_EXPR_NOT_JSON_VALUE =
            define("nop.err.xlang.expr-not-json-value",
                    "表达式不是JSON格式:{expr}", ARG_EXPR);

    ErrorCode ERR_XLANG_EXPR_NOT_QUALIFIED_NAME =
            define("nop.err.xlang.expr-not-qualified-name", "表达式不是合法的名称:{expr}", ARG_EXPR);

    ErrorCode ERR_BIZ_UNKNOWN_BIZ_VAR = define("nop.err.xlang.filter.unknown-biz-var",
            "未定义的前缀引导语法变量:{varName}", ARG_VAR_NAME);

    ErrorCode ERR_BIZ_OBJ_PK_NOT_SIMPLE = define("nop.err.biz.obj-pk-not-simple",
            "对象[{objName}]的主键不是简单类型", ARG_OBJ_NAME);

    ErrorCode ERR_SCRIPT_COMPILE_ERROR = define("nop.err.script.compile-error", "脚本编译报错:{errMsg}", ARG_ERR_MSG);
}
