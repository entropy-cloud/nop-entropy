package io.nop.lint.js.tsc;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protocol serialization round-trips and the fail-visible frame contract
 * (Phase 1 unit matrix, Minimum Rules #25): ready-frame validation, error
 * frames, and field extraction all surface violations instead of returning
 * silent nulls.
 */
public class TscProtocolTest {

    @Test
    public void requestRoundTripsThroughEncodeDecode() {
        Map<String, Object> request = TscProtocol.request(7, "getTypeAtLocation",
                "file", "/a/b.ts", "line", 3, "col", 12);
        String encoded = TscProtocol.encode(request);
        assertTrue(encoded.endsWith("\n"), "frames are newline-delimited");

        Map<String, Object> decoded = TscProtocol.decode(encoded.trim());
        assertEquals(7, TscProtocol.intValue(decoded, "id"));
        assertEquals("getTypeAtLocation", TscProtocol.text(decoded, "op"));
        assertEquals("/a/b.ts", TscProtocol.text(decoded, "file"));
        assertEquals(3, TscProtocol.intValue(decoded, "line"));
        assertEquals(12, TscProtocol.intValue(decoded, "col"));
    }

    @Test
    public void invalidJsonIsABadFrameNotASilentNull() {
        TscQueryException ex = assertThrows(TscQueryException.class,
                () -> TscProtocol.decode("{not json"));
        assertEquals("BAD_FRAME", ex.code());
    }

    @Test
    public void readyFrameWithoutTypescriptIsRejected() {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "ready");
        frame.put("node", "v25.3.0");
        TscBridgeUnavailableException ex = assertThrows(TscBridgeUnavailableException.class,
                () -> TscProtocol.validateReady(frame));
        assertTrue(ex.getMessage().contains("typescript"), "the failure must name the missing binding: "
                + ex.getMessage());
    }

    @Test
    public void nonReadyFirstFrameIsRejected() {
        Map<String, Object> frame = Map.of("type", "fatal", "code", "TYPESCRIPT_MISSING");
        TscBridgeUnavailableException ex = assertThrows(TscBridgeUnavailableException.class,
                () -> TscProtocol.validateReady(frame));
        assertTrue(ex.getMessage().contains("ready"), ex.getMessage());
    }

    @Test
    public void readyFrameWithTypescriptIsAccepted() {
        TscProtocol.validateReady(Map.of("type", "ready", "node", "v25.3.0", "typescript", "5.9.3"));
    }

    @Test
    public void errorFramesCarryTheirStructuredCode() {
        Map<String, Object> frame = TscProtocol.decode(
                "{\"id\":9,\"ok\":false,\"error\":{\"code\":\"NO_PROJECT\",\"message\":\"init first\"}}");
        assertFalse(TscProtocol.isOk(frame));
        TscQueryException ex = TscProtocol.errorOf(9, frame);
        assertEquals("NO_PROJECT", ex.code());
        assertTrue(ex.getMessage().contains("init first"), ex.getMessage());
    }

    @Test
    public void framesWithoutOkFlagAreProtocolViolations() {
        TscQueryException ex = assertThrows(TscQueryException.class,
                () -> TscProtocol.isOk(Map.of("id", 1)));
        assertEquals("BAD_FRAME", ex.code());
    }

    @Test
    public void resultObjectsRoundTrip() {
        Map<String, Object> frame = TscProtocol.decode(
                "{\"id\":2,\"ok\":true,\"result\":{\"type\":\"number\"}}");
        Map<String, Object> result = TscProtocol.resultOf(2, frame);
        assertEquals("number", result.get("type"));
    }
}
