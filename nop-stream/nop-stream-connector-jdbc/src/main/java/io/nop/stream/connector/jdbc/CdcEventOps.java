/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.jdbc;

import io.nop.message.debezium.ChangeEvent;

import java.util.Map;

/**
 * Narrow static facade that concentrates every compile-time link to
 * {@code io.nop.message.debezium.ChangeEvent} inside this module (WI4 plan 2413).
 *
 * <p>{@link JdbcTwoPhaseCommitSink} holds no ChangeEvent reference of its own: it
 * gates the four CDC branches behind a classpath capability probe
 * ({@code CDC_PRESENT}) and only calls into this class when the probe hit. With
 * the probe negative (pure INSERT deployments that do not carry
 * nop-message-debezium), the JVM never resolves this class's ChangeEvent
 * references, so the sink runs zero-linked against the CDC envelope.
 *
 * <p>Contract coupling (rename/move of ChangeEvent = silent capability loss, so
 * keep the three sites in sync): the probe string in
 * {@link JdbcTwoPhaseCommitSink#probeCdcPresent()}, the import below, and the
 * provided+optional nop-message-debezium dependency in the module pom.
 */
final class CdcEventOps {

    /**
     * Probe target FQN. Kept as a compile-time constant so the sink can fold it
     * into its own constant pool without acquiring a class reference here.
     */
    static final String CHANGE_EVENT_CLASS = "io.nop.message.debezium.ChangeEvent";

    private CdcEventOps() {
    }

    static boolean isChangeEvent(Object value) {
        return value instanceof ChangeEvent;
    }

    static String operationOf(Object value) {
        return ((ChangeEvent) value).getOperation();
    }

    static Map<String, Object> afterOf(Object value) {
        return ((ChangeEvent) value).getAfter();
    }

    static Map<String, Object> keyOf(Object value) {
        return ((ChangeEvent) value).getKey();
    }
}
