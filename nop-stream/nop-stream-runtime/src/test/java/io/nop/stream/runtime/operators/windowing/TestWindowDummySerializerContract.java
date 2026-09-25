/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plan 358 Fix-13: the placeholder key serializer's contract is honest.
 * Previously {@code isImmutableType()} claimed {@code true} (aliasing mutable
 * keys) and {@code createInstance()} silently returned {@code null}. The
 * serializer is created by a private factory (no live production callers of
 * copy/createInstance — audit 04), so this test reaches it reflectively.
 */
class TestWindowDummySerializerContract {

    static class NoDefaultConstructor {
        public NoDefaultConstructor(int value) {
        }
    }

    @SuppressWarnings("unchecked")
    private <T> TypeSerializer<T> serializer(Class<T> type) throws Exception {
        java.lang.reflect.Method factory = WindowOperatorFactoryImpl.class
                .getDeclaredMethod("createDummySerializer", Class.class);
        factory.setAccessible(true);
        return (TypeSerializer<T>) factory.invoke(new WindowOperatorFactoryImpl(), type);
    }

    @Test
    void doesNotClaimImmutability() throws Exception {
        TypeSerializer<String> serializer = serializer(String.class);
        assertFalse(serializer.isImmutableType(),
                "an unknown key class must not claim immutability — defensive copies must stay defensive");
        assertSame(serializer, serializer.duplicate());
        assertEqualsNegativeLength(serializer);
    }

    private void assertEqualsNegativeLength(TypeSerializer<?> serializer) {
        org.junit.jupiter.api.Assertions.assertEquals(-1, serializer.getLength());
    }

    @Test
    void createInstanceFailsTypedExceptionForNonDefaultConstructibleClass() throws Exception {
        TypeSerializer<NoDefaultConstructor> serializer = serializer(NoDefaultConstructor.class);
        assertThrows(StreamException.class, serializer::createInstance,
                "createInstance must fail typed instead of silently returning null (guide #24)");
    }

    @Test
    void createInstanceWorksForDefaultConstructibleClass() throws Exception {
        TypeSerializer<String> serializer = serializer(String.class);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(serializer::createInstance);
    }
}
