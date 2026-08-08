package io.nop.integration.feishu.codec;

/**
 * Pbbp2 frame type carried in {@link FeishuStreamFrame#getMethod()}.
 *
 * <p>The numeric {@code method} codes map to the Feishu/Lark Stream long-
 * connection Pbbp2 protocol frame categories (corroborated by the official
 * SDK package {@code com.lark.oapi.core.ws} and the cross-language SDK
 * consistency). Wire-level numeric compatibility with the live Feishu Stream
 * server is verified at the W6 E2E stage (see plan Deferred-But-Adjudicated);
 * the named constants here let any future renumbering happen in one place.
 *
 * <ul>
 *   <li>{@link #CONTROL} ({@code 0}) — handshake / heartbeat.</li>
 *   <li>{@link #DATA} ({@code 1}) — business event payload.</li>
 *   <li>{@link #ACK} ({@code 2}) — acknowledgement.</li>
 * </ul>
 */
public enum FeishuFrameType {
    CONTROL(0),
    DATA(1),
    ACK(2);

    private final int method;

    FeishuFrameType(int method) {
        this.method = method;
    }

    public int getMethod() {
        return method;
    }

    public static FeishuFrameType fromMethod(int method) {
        for (FeishuFrameType t : values()) {
            if (t.method == method) {
                return t;
            }
        }
        return null;
    }
}
