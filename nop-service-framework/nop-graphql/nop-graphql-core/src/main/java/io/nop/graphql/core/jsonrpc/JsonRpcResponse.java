package io.nop.graphql.core.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class JsonRpcResponse<T> {
    // 协议版本（固定值）
    private String jsonrpc = "2.0";

    // 请求标识符（与请求中的id一致）
    private String id;

    // 成功响应结果（与error互斥）
    private T result;

    // 错误响应对象（与result互斥）
    private Error error;

    // 成功响应构造器
    public static <T> JsonRpcResponse<T> success(T result, String id) {
        JsonRpcResponse<T> resp = new JsonRpcResponse<>();
        resp.result = result;
        resp.id = id;
        return resp;
    }

    // 错误响应构造器
    public static JsonRpcResponse<?> error(Error error, String id) {
        JsonRpcResponse<?> resp = new JsonRpcResponse<>();
        resp.error = error;
        resp.id = id;
        return resp;
    }

    public static JsonRpcResponse<?> INVALID_REQUEST(String id) {
        Error error = new Error();
        error.setCode(JsonRpcErrorCodes.INVALID_REQUEST);
        error.setMessage("Invalid Request");
        return error(error, id);
    }

    public static JsonRpcResponse<?> METHOD_NOT_FOUND(String method, String id) {
        Error error = new Error();
        error.setCode(JsonRpcErrorCodes.METHOD_NOT_FOUND);
        error.setMessage("Method not found: " + method);
        return error(error, id);
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getId() {
        return id;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public T getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Error getError() {
        return error;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    // 错误对象内部类
    @DataBean
    public static class Error {
        private int code;
        private String errorCode;
        private String message;
        private Object data;

        // Getters
        public int getCode() {
            return code;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getMessage() {
            return message;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public Object getData() {
            return data;
        }

        public void setCode(int code) {
            this.code = code;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }
}