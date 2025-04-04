package io.nop.graphql.core.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.CollectionHelper;

import java.util.HashMap;
import java.util.Map;

@DataBean
public class JsonRpcRequest {
    // 固定协议版本
    private String jsonrpc = "2.0";

    // 方法名（必填）
    private String method;

    // 参数（支持泛型）
    private Object params;

    // 请求ID（可为字符串/数值/null）
    private String id;

    // 扩展的元数据信息，JSON RPC标准中没有这个部分
    private Map<String, Object> meta;

    private String selection;

    public void mergeHeaders(Map<String, Object> headers) {
        if (headers != null) {
            if (meta == null) {
                this.meta = new HashMap<>(headers);
            } else {
                CollectionHelper.putAllIfAbsent(this.meta, headers);
            }
        }
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getSelection() {
        return selection;
    }

    public void setSelection(String selection) {
        this.selection = selection;
    }
}