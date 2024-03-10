/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface MatchErrors {
    String ARG_JSON_PATH = "jsonPath";
    String ARG_JSON_FIELD = "jsonField";
    String ARG_FILTER_OP = "filterOp";
    String ARG_PATTERN = "pattern";
    String ARG_EXPECTED = "expected";
    String ARG_VALUE = "value";

    String ARG_SIZE = "size";

    String ARG_OPTIONS = "options";
    String ARG_PROP_NAME = "propName";

    String ARG_PATTERN_NAME = "patternName";

    String ARG_PARENT_CLASS = "parentClass";

    String ARG_ALLOWED_NAMES = "allowedNames";

    String ARG_VAR_NAME = "varName";
    String ARG_VAR_VALUE = "varValue";

    String ARG_MIN = "min";
    String ARG_MAX = "max";

    String ARG_EXCLUDE_MIN = "excludeMin";
    String ARG_EXCLUDE_MAX = "excludeMax";

    String ARG_EXPR = "expr";

    ErrorCode ERR_MATCH_COMPARE_OP_MATCH_FAIL = define("nop.err.match.compare-op-match-fail",
            "匹配[{jsonPath}]处的值[{value}]不满足匹配条件:filterOp={filterOp},pattern={pattern}", ARG_JSON_PATH, ARG_VALUE,
            ARG_FILTER_OP, ARG_PATTERN);

    ErrorCode ERR_MATCH_ASSERT_OP_MATCH_FAIL = define("nop.err.match.assert-op-match-fail",
            "匹配[{jsonPath}]处的值[{value}]不满足匹配条件:filterOp={filterOp}", ARG_JSON_PATH, ARG_VALUE, ARG_FILTER_OP);

    ErrorCode ERR_MATCH_CHECK_MATCH_FAIL = define("nop.err.match.check-match-fail", "匹配[{jsonPath}]处的值[{value}]不满足匹配条件",
            ARG_JSON_PATH, ARG_VALUE);

    ErrorCode ERR_MATCH_EXPR_MATCH_FAIL = define("nop.err.match.expr-match-fail",
            "匹配[{jsonPath}]处的值[{value}]不满足匹配条件: expected={expected}", ARG_JSON_PATH, ARG_VALUE, ARG_EXPECTED);

    ErrorCode ERR_MATCH_OBJECT_IS_NULL = define("nop.err.match.object-is-null", "[{jsonPath}]对应的值不应为空", ARG_JSON_PATH,
            ARG_JSON_FIELD);

    ErrorCode ERR_MATCH_FIELD_NOT_OBJECT = define("nop.err.match.field-not-object", "[{jsonPath}]对应的值不是对象类型",
            ARG_JSON_PATH, ARG_JSON_FIELD);

    ErrorCode ERR_MATCH_FIELD_NOT_EXISTS = define("nop.err.match.field-not-exists", "[{jsonPath}]对应的字段在数据中不存在",
            ARG_JSON_PATH, ARG_ALLOWED_NAMES);

    ErrorCode ERR_MATCH_FIELD_NOT_LIST = define("nop.err.match.field-not-list", "[{jsonPath}]对应的值不是列表类型", ARG_JSON_PATH,
            ARG_JSON_FIELD);

    ErrorCode ERR_MATCH_FIELD_VALUE_NOT_NULL = define("nop.err.match.field-value-not-null", "[{jsonPath}]对应的值应该为空，但实际为[{value}]",
            ARG_JSON_PATH, ARG_JSON_FIELD, ARG_VALUE);

    ErrorCode ERR_MATCH_LIST_SIZE_NOT_MATCH = define("nop.err.match.list-size-not-match",
            "[{jsonPath}]对应的列表的大小为{size},不是期待的值：{expected}", ARG_JSON_PATH, ARG_SIZE, ARG_EXPECTED);

    ErrorCode ERR_MATCH_UNKNOWN_PATTERN = define("nop.err.match.unknown-pattern", "未定义的模式匹配：{patternName}",
            ARG_PATTERN_NAME);

    ErrorCode ERR_MATCH_FIELD_VALUE_NOT_EXPECTED = define("nop.err.match.field-value-not-expected",
            "[{jsonPath}]对应的值与期望值不匹配:value={value},expected={expected}", ARG_JSON_PATH, ARG_VALUE, ARG_EXPECTED);

    ErrorCode ERR_MATCH_CONFIG_OPTIONS_NOT_MAP = define("nop.err.match.config-options-not-map",
            "配置对象必须是Map类型或者可以被解析为Map的JSON文本");

    ErrorCode ERR_MATCH_NULL_PATTERN_PROP = define("nop.err.match.null-pattern-prop",
            "匹配模式[{patternName}]的属性[{name}]为空");

    ErrorCode ERR_MATCH_NOT_EQUALS_VAR_VALUE = define("nop.err.match.not-equals-var-value",
            "[{jsonPath}]对应的值[{value}]与变量[{varName}]的值[{varValue}]不匹配", ARG_JSON_PATH, ARG_VALUE, ARG_VAR_NAME,
            ARG_VAR_VALUE);

    ErrorCode ERR_MATCH_INVALID_VAR_NAME = define("nop.err.match.invalid-var-name", "变量名[varName]格式不合法", ARG_VAR_NAME);

    ErrorCode ERR_MATCH_BETWEEN_CHECK_FAIL = define("nop.err.match.between-check-fail",
            "[{jsonPath}]对应的值[{value}]不在有效的区间范围内：filterOp={filterOp},min={min},max={max}", ARG_JSON_PATH, ARG_VALUE,
            ARG_FILTER_OP, ARG_MIN, ARG_MAX);

    ErrorCode ERR_MATCH_INVALID_RANGE_EXPR = define("nop.err.match.invalid-range-expr", "非法的区间表达式，应该是min,max这种形式",
            ARG_EXPR);
}
