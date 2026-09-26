/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep.nfa.sharedbuffer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression proof: {@link NodeId#hashCode()} must be symmetric with its
 * {@code Objects.equals}-based {@link NodeId#equals(Object)} — fields can be
 * null on deserialization-minted instances (the {@code @DataBean} no-arg
 * constructor is a legitimate minting path), so a hand-written hash that
 * dereferences the fields breaks the equals/hashCode contract exactly when
 * equals still works.
 */
class TestNodeIdHashCodeContract {

    @Test
    void allNullFieldsHashCodeDoesNotThrowAndMatchesEquals() {
        NodeId a = new NodeId(null, null);
        NodeId b = new NodeId(null, null);

        assertTrue(a.equals(b), "two all-null NodeIds must be equal");
        assertEquals(a.hashCode(), b.hashCode(),
                "equal objects must have equal hash codes (all-null case)");
    }

    @Test
    void partiallyNullFieldsHashCodeDoesNotThrow() {
        EventId eventId = new EventId(1, 100L);
        NodeId nullEvent = new NodeId(null, "page-1");
        NodeId nullPage = new NodeId(eventId, null);
        NodeId full = new NodeId(eventId, "page-1");

        assertEquals(nullEvent.hashCode(), new NodeId(null, "page-1").hashCode(),
                "equal partially-null objects must have equal hash codes");
        assertEquals(nullPage.hashCode(), new NodeId(eventId, null).hashCode(),
                "equal partially-null objects must have equal hash codes");

        assertNotEquals(nullEvent, full);
        assertNotEquals(nullPage, full);
    }

    @Test
    void mintedInstancesKeepEqualsHashCodeConsistency() {
        EventId e1 = new EventId(1, 100L);
        EventId e2 = new EventId(1, 100L);
        NodeId a = new NodeId(e1, "page-1");
        NodeId b = new NodeId(e2, "page-1");
        NodeId c = new NodeId(e1, "page-2");

        assertTrue(a.equals(b) && a.hashCode() == b.hashCode(),
                "equal minted NodeIds must have equal hash codes");
        assertNotEquals(a, c);
    }
}
