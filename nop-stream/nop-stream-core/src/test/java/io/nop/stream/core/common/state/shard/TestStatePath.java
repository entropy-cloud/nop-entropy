package io.nop.stream.core.common.state.shard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestStatePath {

    @Test
    void testKeyedStatePath() {
        StatePath path = StatePath.forKeyedState("ns1", 100, "op1", 0, 0, "myState");
        assertEquals("checkpoint/ns1/100/op1/0/0/myState", path.getPath());
    }

    @Test
    void testOperatorStatePath() {
        StatePath path = StatePath.forOperatorState("ns1", 100, "op1", 0, "myState");
        assertEquals("checkpoint/ns1/100/op1/0/operator/myState", path.getPath());
    }

    @Test
    void testSourceStatePath() {
        StatePath path = StatePath.forSourceState("ns1", 100, "op1", 0, "split-0");
        assertEquals("checkpoint/ns1/100/op1/0/source/split-0", path.getPath());
    }

    @Test
    void testSinkStatePath() {
        StatePath path = StatePath.forSinkState("ns1", 100, "op1", 0, "txn-1");
        assertEquals("checkpoint/ns1/100/op1/0/sink/txn-1", path.getPath());
    }

    @Test
    void testPathWithoutNamespace() {
        StatePath path = StatePath.forOperatorState("", 100, "op1", 0, "myState");
        assertEquals("checkpoint/100/op1/0/operator/myState", path.getPath());
    }

    @Test
    void testPathEquality() {
        StatePath p1 = StatePath.forKeyedState("ns", 1, "op", 0, 0, "s");
        StatePath p2 = StatePath.forKeyedState("ns", 1, "op", 0, 0, "s");
        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    void testPathDoesNotContainRuntimeIdentities() {
        StatePath path = StatePath.forKeyedState("ns", 1, "op1", 0, 0, "state");
        String p = path.getPath();
        assertFalse(p.contains("deploymentId"));
        assertFalse(p.contains("runId"));
        assertFalse(p.contains("attemptId"));
    }
}
