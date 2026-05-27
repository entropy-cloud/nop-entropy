package io.nop.stream.cep.pattern;

import io.nop.stream.core.exceptions.StreamRuntimeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestMalformedPatternException {

    @Test
    void testExtendsStreamRuntimeException() {
        MalformedPatternException ex = new MalformedPatternException("test message");
        assertTrue(ex instanceof StreamRuntimeException,
                "MalformedPatternException should extend StreamRuntimeException");
    }

    @Test
    void testCaughtByStreamRuntimeExceptionCatch() {
        StreamRuntimeException caught = assertThrows(StreamRuntimeException.class, () -> {
            throw new MalformedPatternException("pattern error");
        });
        // NopException formats message as "ClassName[seq=N,errorCode=msg,params={}]"
        assertTrue(caught.toString().contains("pattern error"),
                "Exception message should contain 'pattern error'");
    }

    @Test
    void testMessageContainsOriginalText() {
        MalformedPatternException ex = new MalformedPatternException("invalid pattern");
        assertTrue(ex.toString().contains("invalid pattern"),
                "Exception toString should contain original message text");
    }
}
