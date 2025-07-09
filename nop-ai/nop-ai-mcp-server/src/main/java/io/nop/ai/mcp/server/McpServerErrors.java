package io.nop.ai.mcp.server;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface McpServerErrors {
    String ARG_PATH = "path";

    ErrorCode ERR_MCP_FILE_NOT_FOUND =
            define("nop.err.mcp.file-not-found", "文件不存在: {path}", ARG_PATH);
}
