package io.nop.rpc.api;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestRpcMethodReferenceFix {

    @Test
    public void testEqualsUsesAndSemantics() {
        RpcMethodReference a1 = new RpcMethodReference("svc", "m1");
        RpcMethodReference a2 = new RpcMethodReference("svc", "m2");

        // 同一个服务的不同方法不应相等
        assertNotEquals(a1, a2);
        assertNotEquals(a1.hashCode(), a2.hashCode());

        assertEquals(new RpcMethodReference("svc", "m1"), a1);
        assertNotEquals(new RpcMethodReference("other", "m1"), a1);
    }

    @Test
    public void testHashSetDedup() {
        Set<RpcMethodReference> set = new HashSet<>();
        set.add(new RpcMethodReference("svc", "m1"));
        set.add(new RpcMethodReference("svc", "m2"));
        set.add(new RpcMethodReference("svc", "m1"));
        assertEquals(2, set.size());
    }
}
