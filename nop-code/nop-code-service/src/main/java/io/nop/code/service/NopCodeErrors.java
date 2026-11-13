package io.nop.code.service;

import io.nop.api.core.exceptions.ErrorCode;
import static io.nop.api.core.exceptions.ErrorCode.define;
public interface NopCodeErrors {

    String ARG_PATH = "path";
    String ARG_FILE_PATH = "filePath";
    String ARG_INDEX_ID = "indexId";
    String ARG_SYMBOL_ID = "symbolId";
    String ARG_FILE_COUNT = "fileCount";
    String ARG_TOTAL_BYTES = "totalBytes";
    String ARG_LIMIT = "limit";

    ErrorCode ERR_NO_ANALYZER_FOR_FILE =
            define("nop.err.code.no-analyzer-for-file", "No analyzer registered for file: {filePath}", ARG_FILE_PATH);

    ErrorCode ERR_INCREMENTAL_FAILED =
            define("nop.err.code.incremental-failed", "Incremental indexing failed");

    ErrorCode ERR_CODE_INVALID_PATH =
            define("nop.err.code.invalid-path", "Invalid path: {path}", ARG_PATH);

    ErrorCode ERR_CODE_FLOW_DETECTOR_NOT_AVAILABLE =
            define("nop.err.code.flow-detector-not-available", "FlowDetector not available");

    ErrorCode ERR_CODE_CHANGE_ANALYZER_NOT_AVAILABLE =
            define("nop.err.code.change-analyzer-not-available", "ChangeAnalyzer not available");

    ErrorCode ERR_CODE_DEAD_CODE_DETECTOR_NOT_AVAILABLE =
            define("nop.err.code.dead-code-detector-not-available", "DeadCodeDetector not available");

    ErrorCode ERR_CODE_SOURCE_CODE_TOO_LARGE =
            define("nop.err.code.source-code-too-large", "Source code exceeds maximum allowed size: {filePath}", ARG_FILE_PATH);

    ErrorCode ERR_CODE_INDEX_FILE_COUNT_EXCEEDED =
            define("nop.err.code.index-file-count-exceeded",
                    "Index directory has too many files: indexId={indexId}, path={path}, fileCount={fileCount}, limit={limit}",
                    ARG_INDEX_ID, ARG_PATH, ARG_FILE_COUNT, ARG_LIMIT);

    ErrorCode ERR_CODE_INDEX_TOTAL_BYTES_EXCEEDED =
            define("nop.err.code.index-total-bytes-exceeded",
                    "Index directory exceeds cumulative size limit: indexId={indexId}, path={path}, totalBytes={totalBytes}, limit={limit}",
                    ARG_INDEX_ID, ARG_PATH, ARG_TOTAL_BYTES, ARG_LIMIT);

    ErrorCode ERR_CODE_REBUILD_NOT_GIT_REPO =
            define("nop.err.code.rebuild-not-git-repo", "projectPath is not a git repository root: {path}", ARG_PATH);

    ErrorCode ERR_CODE_REBUILD_HEAD_MISMATCH =
            define("nop.err.code.rebuild-head-mismatch",
                    "Worktree HEAD does not match targetCommitish: head={head}, target={target}");

    ErrorCode ERR_CODE_REBUILD_INVALID_GIT_REF =
            define("nop.err.code.rebuild-invalid-git-ref", "Invalid git ref: {gitRef}", "gitRef");
}
