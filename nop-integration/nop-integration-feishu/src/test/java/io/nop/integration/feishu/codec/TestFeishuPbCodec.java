package io.nop.integration.feishu.codec;

import io.nop.integration.feishu.NopFeishuException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip + malformed-input tests for {@link FeishuPbCodec}. The codec is
 * pure byte in/out (no network/SDK), so these tests fully exercise the Pbbp2
 * frame encode/decode path.
 */
class TestFeishuPbCodec {

    @Test
    void encodeThenDecodeRoundTripsControlFrame() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("type", "connect");
        headers.put("log_id", "log-1");
        FeishuStreamFrame frame = new FeishuStreamFrame(
                FeishuFrameType.CONTROL.getMethod(), headers, FeishuStreamFrame.EMPTY_PAYLOAD, "req-1");

        byte[] encoded = FeishuPbCodec.encode(frame);
        assertNotNull(encoded);

        FeishuStreamFrame decoded = FeishuPbCodec.decode(encoded);
        assertEquals(FeishuFrameType.CONTROL.getMethod(), decoded.getMethod());
        assertEquals("connect", decoded.getHeaders().get("type"));
        assertEquals("log-1", decoded.getHeaders().get("log_id"));
        assertEquals("req-1", decoded.getRequestId());
        // CONTROL frame has empty payload by construction
        assertNotNull(decoded.getPayload());
        assertEquals(0, decoded.getPayload().length);
    }

    @Test
    void encodeThenDecodeRoundTripsDataFrame() {
        byte[] payload = "{\"event\":\"im.message.receive_v1\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("type", "im.message.receive_v1");
        FeishuStreamFrame frame = new FeishuStreamFrame(
                FeishuFrameType.DATA.getMethod(), headers, payload, null);

        byte[] encoded = FeishuPbCodec.encode(frame);
        FeishuStreamFrame decoded = FeishuPbCodec.decode(encoded);

        assertEquals(FeishuFrameType.DATA.getMethod(), decoded.getMethod());
        assertArrayEquals(payload, decoded.getPayload());
        assertEquals("im.message.receive_v1", decoded.getHeaders().get("type"));
        assertNull(decoded.getRequestId());
    }

    @Test
    void encodeThenDecodeRoundTripsAckFrame() {
        FeishuStreamFrame frame = new FeishuStreamFrame(
                FeishuFrameType.ACK.getMethod(), null, FeishuStreamFrame.EMPTY_PAYLOAD, "ack-99");

        byte[] encoded = FeishuPbCodec.encode(frame);
        FeishuStreamFrame decoded = FeishuPbCodec.decode(encoded);

        assertEquals(FeishuFrameType.ACK.getMethod(), decoded.getMethod());
        assertEquals("ack-99", decoded.getRequestId());
    }

    @Test
    void encodeBinaryPayloadRoundTripsByteForByte() {
        // payload with all byte values to prove raw bytes are preserved
        byte[] payload = new byte[256];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        FeishuStreamFrame frame = new FeishuStreamFrame(
                FeishuFrameType.DATA.getMethod(), null, payload, null);

        FeishuStreamFrame decoded = FeishuPbCodec.decode(FeishuPbCodec.encode(frame));
        assertArrayEquals(payload, decoded.getPayload());
    }

    @Test
    void proto3DefaultMethodZeroRoundTrips() {
        // method=0 (CONTROL) is the proto3 default and must survive round-trip
        FeishuStreamFrame frame = new FeishuStreamFrame(0, null, null, null);
        FeishuStreamFrame decoded = FeishuPbCodec.decode(FeishuPbCodec.encode(frame));
        assertEquals(0, decoded.getMethod());
    }

    @Test
    void decodeMalformedBytesFailsExplicitly() {
        // truncated: a length-delimited header that declares more bytes than present
        byte[] truncated = new byte[]{0x12, 0x10, 0x0A, 0x01, 0x61};
        NopFeishuException ex = assertThrows(NopFeishuException.class,
                () -> FeishuPbCodec.decode(truncated));
        assertTrue(ex.getMessage().contains("exceeds remaining bytes"));

        // empty bytes are a valid proto3 all-default frame, NOT malformed;
        // only null input is rejected as invalid
        assertThrows(NopFeishuException.class, () -> FeishuPbCodec.decode(null));
    }

    @Test
    void decodeTruncatedVarintFailsExplicitly() {
        // a varint with continuation bit set but no following byte
        byte[] badVarint = new byte[]{(byte) 0x80};
        assertThrows(NopFeishuException.class, () -> FeishuPbCodec.decode(badVarint));
    }

    @Test
    void encodeNullFrameFailsExplicitly() {
        assertThrows(NopFeishuException.class, () -> FeishuPbCodec.encode(null));
    }
}
