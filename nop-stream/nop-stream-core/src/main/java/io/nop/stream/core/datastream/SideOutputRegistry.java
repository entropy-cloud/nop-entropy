/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.nop.stream.core.streamrecord.StreamRecord;

/**
 * G-2+09e① (plan 369 Phase 2): registry bridging plan-time side-output consumption
 * ({@code getSideOutput(tag).sink(...)} / {@code print()} / {@code collect(...)}) to the
 * runtime output protocol. Producers emit tagged records via
 * {@code Output#collect(OutputTag, record)}; consumers ({@code ChainingOutput}, the
 * cross-task {@code StreamTaskInvokable} routing) look the consumer up here when their
 * task-local registration map has no entry for the tag.
 *
 * <p>W-07 (CRA 8.42 R4-02, ruling C.2 — 1.1-FAM-2: C1-002 + C5-007 + C7-006): the
 * registry is EXECUTION-CONTEXT-LOCAL, no longer JVM-static. One instance is hung on
 * one {@link StreamExecutionEnvironment} ({@code env.getSideOutputRegistry()}):
 * <ul>
 *   <li><b>Scope</b>: two executions on the same host register into separate
 *       instances — a same-tag registration can never topple another execution's
 *       consumer (the pre-fix static map's silent cross-job mis-delivery is
 *       structurally impossible; the key space is physically separated, not
 *       warned or validated).</li>
 *   <li><b>Lifecycle</b>: the registry lives and dies with the env's object graph —
 *       a terminal execution's registrations (and the operator chains their
 *       consumers close over) become unreachable together, with no explicit
 *       remove wiring at the terminal-state anchors. {@link #remove(String)} stays
 *       available as an explicit in-execution de-registration API; {@link #clear()}
 *       is the per-execution isolation helper (tests).</li>
 *   <li><b>Remote/分进程 form</b> (ruling C.2 / F-3): invokables built by the remote
 *       plan builder carry NO registry — their read-point miss answers the existing
 *       {@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER} fail-fast, which is exactly the
 *       pre-existing split-process semantics (side outputs were always JVM-local;
 *       this ruling tightens the embedded same-JVM form to execution-context-local
 *       for parity, it does not widen anything).</li>
 * </ul>
 *
 * <p>Re-registering the same tag id within one scope replaces the previous consumer
 * (unchanged semantics; {@code OutputTag} equality is id-based).
 */
public final class SideOutputRegistry {

    private final Map<String, Consumer<StreamRecord<?>>> consumers = new ConcurrentHashMap<>();

    /**
     * Creates one execution-context-local registry (one per
     * {@code StreamExecutionEnvironment}).
     */
    public SideOutputRegistry() {
    }

    /**
     * Registers (or replaces) the consumer for the given side-output tag id
     * within this execution scope.
     */
    public void register(String tagId, Consumer<StreamRecord<?>> consumer) {
        if (tagId == null || tagId.isEmpty()) {
            throw new IllegalArgumentException("Side-output tag id must not be null or empty");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("Side-output consumer must not be null");
        }
        consumers.put(tagId, consumer);
    }

    /**
     * Returns the consumer registered for the given tag id in this scope, or
     * {@code null} when none is registered (the caller keeps its own no-consumer
     * fail-fast semantics).
     */
    public Consumer<StreamRecord<?>> get(String tagId) {
        if (tagId == null || tagId.isEmpty()) {
            return null;
        }
        return consumers.get(tagId);
    }

    /**
     * Removes the registration for the given tag id (explicit in-execution
     * de-registration).
     */
    public void remove(String tagId) {
        if (tagId != null) {
            consumers.remove(tagId);
        }
    }

    /**
     * Removes all registrations of this scope (isolation helper).
     */
    public void clear() {
        consumers.clear();
    }
}
