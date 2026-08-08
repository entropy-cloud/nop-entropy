package io.nop.integration.feishu.codec;

import io.nop.integration.feishu.NopFeishuException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encoder/decoder for Feishu/Lark Stream Pbbp2 frames using the protobuf wire
 * format (published standard: https://protobuf.dev/programming-guides/encoding/).
 *
 * <p>The Frame message has four fields (see {@link FeishuStreamFrame}); this
 * codec hand-implements the wire format for that single message so the module
 * avoids pulling in {@code protobuf-java} (which is not managed by
 * {@code nop-bom}). The codec is independently testable: pure byte in/out,
 * no network or SDK dependency.
 *
 * <p><b>No silent no-op</b>: {@link #encode(FeishuStreamFrame)} on a
 * {@code null} frame and {@link #decode(byte[])} on {@code null} or malformed
 * bytes both throw {@link NopFeishuException} rather than returning
 * {@code null}/empty.
 *
 * <p><b>Proto3 defaults</b>: zero-valued scalar fields and empty
 * length-delimited fields are omitted on encode and reconstituted as defaults
 * on decode (so {@code method=0} CONTROL round-trips correctly).
 */
public final class FeishuPbCodec {

    // field numbers
    private static final int FIELD_METHOD = 1;
    private static final int FIELD_HEADERS = 2;
    private static final int FIELD_PAYLOAD = 3;
    private static final int FIELD_REQUEST_ID = 4;

    // wire types
    private static final int WIRE_VARINT = 0;
    private static final int WIRE_LENGTH = 2;
    private static final int WIRE_FIXED64 = 1;
    private static final int WIRE_FIXED32 = 5;

    private FeishuPbCodec() {
    }

    public static byte[] encode(FeishuStreamFrame frame) {
        if (frame == null) {
            throw new NopFeishuException("FeishuPbCodec.encode: frame must not be null");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int method = frame.getMethod();
        if (method != 0) {
            writeTag(out, FIELD_METHOD, WIRE_VARINT);
            writeVarint(out, method);
        }

        Map<String, String> headers = frame.getHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                byte[] sub = encodeHeaderEntry(e.getKey(), e.getValue());
                writeTag(out, FIELD_HEADERS, WIRE_LENGTH);
                writeVarint(out, sub.length);
                out.write(sub, 0, sub.length);
            }
        }

        byte[] payload = frame.getPayload();
        if (payload != null && payload.length > 0) {
            writeTag(out, FIELD_PAYLOAD, WIRE_LENGTH);
            writeVarint(out, payload.length);
            out.write(payload, 0, payload.length);
        }

        String requestId = frame.getRequestId();
        if (requestId != null && !requestId.isEmpty()) {
            byte[] rb = requestId.getBytes(StandardCharsets.UTF_8);
            writeTag(out, FIELD_REQUEST_ID, WIRE_LENGTH);
            writeVarint(out, rb.length);
            out.write(rb, 0, rb.length);
        }

        return out.toByteArray();
    }

    public static FeishuStreamFrame decode(byte[] bytes) {
        if (bytes == null) {
            throw new NopFeishuException("FeishuPbCodec.decode: bytes must not be null");
        }
        // Per proto3, empty bytes are a valid encoding of a message whose fields
        // are all at their defaults (method=0, no headers, empty payload). This
        // mirrors protobuf-java's parseFrom(emptyBytes) which returns the default
        // instance. Genuinely malformed (truncated / bad varint) input is rejected
        // below.
        if (bytes.length == 0) {
            return new FeishuStreamFrame(0, null, FeishuStreamFrame.EMPTY_PAYLOAD, null);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);

        int method = 0;
        Map<String, String> headers = null;
        byte[] payload = FeishuStreamFrame.EMPTY_PAYLOAD;
        String requestId = null;

        while (buf.hasRemaining()) {
            int tag = readVarint(buf);
            int fieldNumber = tag >>> 3;
            int wireType = tag & 0x07;
            switch (fieldNumber) {
                case FIELD_METHOD:
                    expectWireType(fieldNumber, wireType, WIRE_VARINT);
                    method = readVarint(buf);
                    break;
                case FIELD_HEADERS:
                    expectWireType(fieldNumber, wireType, WIRE_LENGTH);
                    byte[] entry = readLengthDelimited(buf, "header entry");
                    if (headers == null) {
                        headers = new LinkedHashMap<>();
                    }
                    decodeHeaderEntry(entry, headers);
                    break;
                case FIELD_PAYLOAD:
                    expectWireType(fieldNumber, wireType, WIRE_LENGTH);
                    payload = readLengthDelimited(buf, "payload");
                    break;
                case FIELD_REQUEST_ID:
                    expectWireType(fieldNumber, wireType, WIRE_LENGTH);
                    byte[] rb = readLengthDelimited(buf, "requestId");
                    requestId = new String(rb, StandardCharsets.UTF_8);
                    break;
                default:
                    skipField(buf, wireType);
                    break;
            }
        }
        return new FeishuStreamFrame(method, headers, payload, requestId);
    }

    private static byte[] encodeHeaderEntry(String key, String value) {
        ByteArrayOutputStream sub = new ByteArrayOutputStream();
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);
        writeTag(sub, 1, WIRE_LENGTH);
        writeVarint(sub, kb.length);
        sub.write(kb, 0, kb.length);
        if (value != null) {
            byte[] vb = value.getBytes(StandardCharsets.UTF_8);
            writeTag(sub, 2, WIRE_LENGTH);
            writeVarint(sub, vb.length);
            sub.write(vb, 0, vb.length);
        }
        return sub.toByteArray();
    }

    private static void decodeHeaderEntry(byte[] entryBytes, Map<String, String> headers) {
        ByteBuffer eb = ByteBuffer.wrap(entryBytes);
        String key = null;
        String value = "";
        while (eb.hasRemaining()) {
            int tag = readVarint(eb);
            int fn = tag >>> 3;
            int wt = tag & 0x07;
            if (fn == 1) {
                key = new String(readLengthDelimited(eb, "header key"), StandardCharsets.UTF_8);
            } else if (fn == 2) {
                value = new String(readLengthDelimited(eb, "header value"), StandardCharsets.UTF_8);
            } else {
                skipField(eb, wt);
            }
        }
        if (key == null) {
            throw new NopFeishuException("FeishuPbCodec.decode: header entry missing key field");
        }
        headers.put(key, value);
    }

    private static void writeTag(ByteArrayOutputStream out, int fieldNumber, int wireType) {
        writeVarint(out, (fieldNumber << 3) | wireType);
    }

    private static void writeVarint(ByteArrayOutputStream out, int value) {
        long v = value & 0xFFFFFFFFL;
        while (true) {
            if ((v & ~0x7FL) == 0) {
                out.write((int) v);
                return;
            }
            out.write((int) (v & 0x7F) | 0x80);
            v >>>= 7;
        }
    }

    private static int readVarint(ByteBuffer buf) {
        long result = 0;
        int shift = 0;
        while (true) {
            if (!buf.hasRemaining()) {
                throw new NopFeishuException("FeishuPbCodec.decode: unexpected end of frame while reading varint");
            }
            byte b = buf.get();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
            if (shift >= 32) {
                throw new NopFeishuException("FeishuPbCodec.decode: varint exceeds 32 bits");
            }
        }
        return (int) result;
    }

    private static byte[] readLengthDelimited(ByteBuffer buf, String what) {
        int len = readVarint(buf);
        if (len < 0) {
            throw new NopFeishuException("FeishuPbCodec.decode: negative length for " + what);
        }
        if (buf.remaining() < len) {
            throw new NopFeishuException("FeishuPbCodec.decode: declared length " + len
                    + " for " + what + " exceeds remaining bytes " + buf.remaining());
        }
        byte[] data = new byte[len];
        buf.get(data);
        return data;
    }

    private static void expectWireType(int fieldNumber, int actual, int expected) {
        if (actual != expected) {
            throw new NopFeishuException("FeishuPbCodec.decode: field " + fieldNumber
                    + " expected wire type " + expected + " but got " + actual);
        }
    }

    private static void skipField(ByteBuffer buf, int wireType) {
        switch (wireType) {
            case WIRE_VARINT:
                readVarint(buf);
                break;
            case WIRE_LENGTH:
                int len = readVarint(buf);
                if (len < 0 || buf.remaining() < len) {
                    throw new NopFeishuException("FeishuPbCodec.decode: invalid length while skipping field");
                }
                buf.position(buf.position() + len);
                break;
            case WIRE_FIXED64:
                requireRemaining(buf, 8, "fixed64");
                buf.position(buf.position() + 8);
                break;
            case WIRE_FIXED32:
                requireRemaining(buf, 4, "fixed32");
                buf.position(buf.position() + 4);
                break;
            default:
                throw new NopFeishuException("FeishuPbCodec.decode: unsupported wire type " + wireType);
        }
    }

    private static void requireRemaining(ByteBuffer buf, int needed, String what) {
        if (buf.remaining() < needed) {
            throw new NopFeishuException("FeishuPbCodec.decode: need " + needed + " bytes for " + what
                    + " but only " + buf.remaining() + " remain");
        }
    }
}
