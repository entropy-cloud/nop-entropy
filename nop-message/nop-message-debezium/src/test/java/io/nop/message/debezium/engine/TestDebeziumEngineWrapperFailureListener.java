/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium.engine;

import io.nop.api.core.exceptions.NopException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 368 Phase 4 (audit R5-CON-03): {@link DebeziumEngineWrapper} must expose the
 * engine's terminal failure (run() throwing / CompletionCallback success=false) to the
 * owner. The wrapper instances are created internally by DebeziumMessageSource, so the
 * exposure surface is the connector-name-keyed failure listener registry — the same
 * identity the offset registry already keys by. This test pins the registry semantics;
 * the end-to-end wiring into the owning task is covered by nop-stream-connector-debezium's
 * TestDebeziumCdcSourceErrorPropagation.
 */
class TestDebeziumEngineWrapperFailureListener {

    @AfterEach
    void cleanUp() {
        DebeziumEngineWrapper.unregisterFailureListener("wl-test");
        DebeziumEngineWrapper.unregisterFailureListener("wl-other");
    }

    @Test
    void testRegisteredListenerReceivesEngineFailure() {
        List<Throwable> received = new CopyOnWriteArrayList<>();
        Consumer<Throwable> listener = received::add;

        DebeziumEngineWrapper.registerFailureListener("wl-test", listener);
        RuntimeException failure = new RuntimeException("engine terminal failure");
        DebeziumEngineWrapper.notifyEngineFailure("wl-test", failure);

        assertEquals(1, received.size());
        assertEquals(failure, received.get(0));
    }

    @Test
    void testUnregisteredListenerIsNotInvoked() {
        List<Throwable> received = new CopyOnWriteArrayList<>();
        DebeziumEngineWrapper.registerFailureListener("wl-test", received::add);
        DebeziumEngineWrapper.unregisterFailureListener("wl-test");

        DebeziumEngineWrapper.notifyEngineFailure("wl-test", new RuntimeException("after unregister"));

        assertTrue(received.isEmpty(), "no listener may fire after unregistration");
    }

    @Test
    void testListenerFailureDoesNotBreakNotification() {
        List<Throwable> received = new CopyOnWriteArrayList<>();
        DebeziumEngineWrapper.registerFailureListener("wl-test", t -> {
            received.add(t);
            throw new IllegalStateException("listener bug");
        });

        RuntimeException failure = new RuntimeException("engine failure");
        DebeziumEngineWrapper.notifyEngineFailure("wl-test", failure);

        assertEquals(1, received.size(),
                "a throwing listener must not prevent the notification and must not propagate");
    }

    @Test
    void testInvalidRegistrationRejected() {
        assertThrows(NopException.class,
                () -> DebeziumEngineWrapper.registerFailureListener(null, t -> {
                }));
        assertThrows(NopException.class,
                () -> DebeziumEngineWrapper.registerFailureListener("", t -> {
                }));
        assertThrows(NopException.class,
                () -> DebeziumEngineWrapper.registerFailureListener("wl-test", null));
    }
}
