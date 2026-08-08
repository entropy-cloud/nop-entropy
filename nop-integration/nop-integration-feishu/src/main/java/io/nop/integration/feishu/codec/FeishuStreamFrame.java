package io.nop.integration.feishu.codec;

import io.nop.api.core.annotations.data.DataBean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One Pbbp2 frame exchanged over the Feishu/Lark Stream WebSocket
 * long-connection. Serialized by {@link FeishuPbCodec} using the protobuf
 * wire format.
 *
 * <p><b>Fields</b> (proto-style):
 * <ul>
 *   <li>{@code method} (field 1, varint) — frame category; see
 *       {@link FeishuFrameType}. {@code 0}=CONTROL, {@code 1}=DATA,
 *       {@code 2}=ACK.</li>
 *   <li>{@code headers} (field 2, {@code map<string,string>}) — frame
 *       metadata. In the real Feishu protocol the event {@code type},
 *       {@code log_id} and similar are carried as header entries.</li>
 *   <li>{@code payload} (field 3, {@code bytes}) — frame body. For DATA
 *       frames this is the serialized event (e.g. the
 *       {@code im.message.receive_v1} event JSON).</li>
 *   <li>{@code requestId} (field 4, {@code string}) — optional correlation
 *       id.</li>
 * </ul>
 *
 * <p>This is an internal transport data model (not a public API contract),
 * so it follows the Nop {@code @DataBean} convention like the other
 * integration models.
 */
@DataBean
public class FeishuStreamFrame {

    public static final byte[] EMPTY_PAYLOAD = new byte[0];

    private int method;
    private Map<String, String> headers;
    private byte[] payload;
    private String requestId;

    public FeishuStreamFrame() {
    }

    public FeishuStreamFrame(int method, Map<String, String> headers, byte[] payload, String requestId) {
        this.method = method;
        this.headers = headers;
        this.payload = payload;
        this.requestId = requestId;
    }

    public int getMethod() {
        return method;
    }

    public void setMethod(int method) {
        this.method = method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public FeishuStreamFrame method(int m) {
        this.method = m;
        return this;
    }

    public FeishuStreamFrame header(String key, String value) {
        if (this.headers == null) {
            this.headers = new LinkedHashMap<>();
        }
        this.headers.put(key, value);
        return this;
    }

    public FeishuStreamFrame payload(byte[] p) {
        this.payload = p;
        return this;
    }

    public FeishuStreamFrame requestId(String id) {
        this.requestId = id;
        return this;
    }
}
