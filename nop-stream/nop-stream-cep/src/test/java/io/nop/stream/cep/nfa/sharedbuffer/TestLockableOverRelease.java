package io.nop.stream.cep.nfa.sharedbuffer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLockableOverRelease {

    @Test
    void testOverReleaseThrowsException() {
        Lockable<String> lockable = new Lockable<>("test", 0);
        IllegalStateException ex = assertThrows(IllegalStateException.class, lockable::release);
        assertTrue(ex.getMessage().contains("over-release"));
    }

    @Test
    void testDoubleReleaseThrowsException() {
        Lockable<String> lockable = new Lockable<>("test", 1);
        assertTrue(lockable.release());
        assertThrows(IllegalStateException.class, lockable::release);
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
        assertThrows(IllegalStateException.class, lockable::release);
        assertEquals(0, lockable.getRefCounter());
    }
}
