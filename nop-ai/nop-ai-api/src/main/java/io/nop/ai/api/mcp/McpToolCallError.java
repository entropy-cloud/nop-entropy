package io.nop.ai.api.mcp;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class McpToolCallError {
    private String code;
    private String message;
    private Map<String, Object> details;

    // 构造函数
    public McpToolCallError(String code, String message) {
        this(code, message, null);
    }

    public McpToolCallError(String code, String message, Map<String, Object> details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }

    // Getters and Setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    // 常用错误码
    public static final String TOOL_NOT_FOUND = "TOOL_NOT_FOUND";
    public static final String INVALID_PARAMETERS = "INVALID_PARAMETERS";
    public static final String EXECUTION_TIMEOUT = "EXECUTION_TIMEOUT";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}