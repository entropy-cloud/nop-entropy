/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

/**
 * 网关协议层业务异常（D1 §3.7）：携带 JSON-RPC code + 平台字符串 errorCode +
 * 可选的关闭码（错误响应先发、随后按 D1 §3.8 关闭连接）。
 */
public class DebugProtocolException extends RuntimeException {
    private final int jsonRpcCode;
    private final String errorCode;
    private final String protocolMessage;
    private final transient Object data;
    private final short closeCode;

    public DebugProtocolException(int jsonRpcCode, String message) {
        this(jsonRpcCode, null, message, null, (short) 0);
    }

    public DebugProtocolException(int jsonRpcCode, String errorCode, String message, Object data, short closeCode) {
        super(message);
        this.jsonRpcCode = jsonRpcCode;
        this.errorCode = errorCode;
        this.protocolMessage = message;
        this.data = data;
        this.closeCode = closeCode;
    }

    /** 业务错误（D1 §3.7：-32000 + error.errorCode + error.data）。 */
    public static DebugProtocolException biz(String errorCode, String message) {
        return new DebugProtocolException(-32000, errorCode, message, null, (short) 0);
    }

    /** 参数校验错误（-32602）。 */
    public static DebugProtocolException invalidParams(String message) {
        return new DebugProtocolException(-32602, message);
    }

    public int getJsonRpcCode() {
        return jsonRpcCode;
    }

    /** 线上面错误消息（与 RuntimeException.getMessage 解耦，供错误响应构造）。 */
    public String getProtocolMessage() {
        return protocolMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getData() {
        return data;
    }

    /** 非零时错误响应发送后随即按该关闭码关闭连接（D1 §3.8）。 */
    public short getCloseCode() {
        return closeCode;
    }
}
