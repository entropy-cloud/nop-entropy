package io.nop.ai.mcp.server;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface McpServerErrors {
    String ARG_PATH = "path";
    String ARG_FILE_TYPE = "fileType";

    ErrorCode ERR_MCP_FILE_NOT_FOUND =
            define("nop.err.mcp.file-not-found", "文件不存在: {path}", ARG_PATH);

    ErrorCode ERR_MCP_NO_XDEF_FOR_FILE_TYPE =
            define("nop.err.mcp.no-xdef-for-file-type", "No xdef defined for file type: {fileType}", ARG_FILE_TYPE);

    ErrorCode ERR_MCP_MERGE_NOT_SUPPORTED =
            define("nop.err.mcp.merge-not-supported", "File type is not supported for merge (no xdef): {fileType}", ARG_FILE_TYPE);
}
