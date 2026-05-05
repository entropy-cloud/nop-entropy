package io.nop.code.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopCodeErrors {

    String ARG_PATH = "path";
    String ARG_FILE_PATH = "filePath";
    String ARG_INDEX_ID = "indexId";
    String ARG_SYMBOL_ID = "symbolId";

    ErrorCode ERR_INDEX_DIRECTORY_FAILED =
            define("nop.err.code.index-directory-failed", "索引目录失败: {path}", ARG_PATH);

    ErrorCode ERR_NO_ANALYZER_FOR_FILE =
            define("nop.err.code.no-analyzer-for-file", "没有为文件注册分析器: {filePath}", ARG_FILE_PATH);

    ErrorCode ERR_INCREMENTAL_NOT_SUPPORTED =
            define("nop.err.code.incremental-not-supported", "增量索引需要CodeIndexService实现");

    ErrorCode ERR_INCREMENTAL_FAILED =
            define("nop.err.code.incremental-failed", "增量索引失败");

    ErrorCode ERR_INDEX_NOT_FOUND =
            define("nop.err.code.index-not-found", "索引未找到: {indexId}", ARG_INDEX_ID);

    ErrorCode ERR_SYMBOL_NOT_FOUND =
            define("nop.err.code.symbol-not-found", "符号未找到: {symbolId}", ARG_SYMBOL_ID);
}
