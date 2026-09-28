package io.nop.jpath;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface NopJqErrors {
    String ARG_EXPR = "expr";
    String ARG_PATH = "path";
    String ARG_INDEX = "index";
    String ARG_ITEM = "item";

    ErrorCode ERR_JQ_INVALID_PATH = define("NOP_JQ-001",
            "Invalid JsonPath expression: {path}");

    ErrorCode ERR_JQ_COMPILE_ERROR = define("NOP_JQ-002",
            "Failed to compile JsonPath expression: {expr}");

    ErrorCode ERR_JQ_INDEX_OUT_OF_BOUNDS = define("NOP_JQ-003",
            "Array index out of bounds: {index}");

    ErrorCode ERR_JQ_NOT_ARRAY = define("NOP_JQ-004",
            "Expected array but got {item}");

    ErrorCode ERR_JQ_NOT_OBJECT = define("NOP_JQ-005",
            "Expected object but got {item}");

    ErrorCode ERR_JQ_FILTER_ERROR = define("NOP_JQ-006",
            "Filter evaluation failed for expression: {expr}");

    ErrorCode ERR_JQ_SET_FAILED = define("NOP_JQ-007",
            "Failed to set value at path: {path}");

    ErrorCode ERR_JQ_REMOVE_FAILED = define("NOP_JQ-008",
            "Failed to remove value at path: {path}");
}
