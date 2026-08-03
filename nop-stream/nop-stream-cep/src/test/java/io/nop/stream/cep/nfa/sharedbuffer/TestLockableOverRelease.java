package io.nop.stream.cep.nfa.sharedbuffer;

import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.exceptions.StreamRuntimeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLockableOverRelease {

    @Test
    void testOverReleaseThrowsPlatformException() {
        Lockable<String> lockable = new Lockable<>("test", 0);
        StreamRuntimeException ex = assertThrows(StreamRuntimeException.class, lockable::release);
        assertTrue(ex.getMessage().contains("over-release"));
        assertTrue(ex instanceof NopException,
                "Lockable over-release must use the Nop platform exception system, not bare IllegalStateException");
    }

    @Test
    void testOverReleaseDoesNotThrowBareIllegalStateException() {
        Lockable<String> lockable = new Lockable<>("test", 0);
        Throwable thrown = assertThrows(Throwable.class, lockable::release);
        assertFalse(thrown instanceof IllegalStateException,
                "Lockable over-release must not bypass the platform exception system by throwing bare IllegalStateException");
    }

    @Test
    void testDoubleReleaseThrowsPlatformException() {
        Lockable<String> lockable = new Lockable<>("test", 1);
        assertTrue(lockable.release());
        assertThrows(StreamRuntimeException.class, lockable::release);
    }

    @Test
    void testNormalReleaseSequence() {
        Lockable<String> lockable = new Lockable<>("test", 3);
        assertFalse(lockable.release());
        assertFalse(lockable.release());
        assertTrue(lockable.release());
        assertEquals(0, lockable.getRefCounter());
    }

    @Test
    void testRefCounterResetsToZeroOnOverRelease() {
        Lockable<String> lockable = new Lockable<>("test", 0);
        assertThrows(StreamRuntimeException.class, lockable::release);
        assertEquals(0, lockable.getRefCounter());
    }

    @Test
    void testReleaseOrDetachThrowsPlatformExceptionOnNegative() {
        Lockable<String> lockable = new Lockable<>("test", -1);
        assertThrows(StreamRuntimeException.class, lockable::releaseOrDetach);
        assertEquals(0, lockable.getRefCounter());
    }
}
