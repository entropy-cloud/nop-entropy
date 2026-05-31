package io.nop.code.service;

import io.nop.api.core.exceptions.ErrorCode;
import static io.nop.api.core.exceptions.ErrorCode.define;
public interface NopCodeErrors {

    String ARG_PATH = "path";
    String ARG_FILE_PATH = "filePath";
    String ARG_INDEX_ID = "indexId";
    String ARG_SYMBOL_ID = "symbolId";

    ErrorCode ERR_NO_ANALYZER_FOR_FILE =
            define("nop.err.code.no-analyzer-for-file", "没有为文件注册分析器: {filePath}", ARG_FILE_PATH);

    ErrorCode ERR_INCREMENTAL_FAILED =
            define("nop.err.code.incremental-failed", "增量索引失败");

    ErrorCode ERR_CODE_INVALID_PATH =
            define("nop.err.code.invalid-path", "Invalid path: {path}", ARG_PATH);

    ErrorCode ERR_CODE_FLOW_DETECTOR_NOT_AVAILABLE =
            define("nop.err.code.flow-detector-not-available", "FlowDetector not available");

    ErrorCode ERR_CODE_CHANGE_ANALYZER_NOT_AVAILABLE =
            define("nop.err.code.change-analyzer-not-available", "ChangeAnalyzer not available");

    ErrorCode ERR_CODE_DEAD_CODE_DETECTOR_NOT_AVAILABLE =
            define("nop.err.code.dead-code-detector-not-available", "DeadCodeDetector not available");
}
