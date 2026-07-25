/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CURRENT_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TARGET_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

/**
 * Unified execution state-transition validator used by both {@link Task} and
 * {@link SubtaskTask}. The two enums are kept as separate types (existing
 * references), but they share the same transition model so G58 ("统一 cancel
 * 语义") is enforced uniformly.
 *
 * <p>State names common to both enums:
 * <ul>
 *   <li>{@code CREATED} — initial</li>
 *   <li>{@code SCHEDULED}, {@code DEPLOYING}, {@code RECOVERING} — lifecycle intermediates (G54)</li>
 *   <li>{@code RUNNING} — actively executing</li>
 *   <li>{@code CANCELING} — cooperative cancel intermediate (G58)</li>
 *   <li>{@code COMPLETED}, {@code FAILED}, {@code CANCELED} — terminal (absorbing)</li>
 * </ul>
 *
 * <p>Explicit legal transitions (everything else throws {@link StreamException}
 * with {@code ERR_STREAM_INVALID_STATE} — no silent skips, per anti-hollow rule #24):
 * <ul>
 *   <li>{@code CREATED → SCHEDULED, CANCELING, FAILED}</li>
 *   <li>{@code SCHEDULED → DEPLOYING, RECOVERING, CANCELING, FAILED}</li>
 *   <li>{@code DEPLOYING → RUNNING, RECOVERING, CANCELING, FAILED}</li>
 *   <li>{@code RUNNING → COMPLETED, RECOVERING, CANCELING, FAILED}</li>
 *   <li>{@code RECOVERING → SCHEDULED, CANCELING, FAILED}</li>
 *   <li>{@code CANCELING → CANCELED}</li>
 *   <li>Terminal states (COMPLETED/FAILED/CANCELED) — no transitions out</li>
 * </ul>
 *
 * <p>Self-loops are never legal. Cross-stage skips (e.g. CREATED→RUNNING) are
 * illegal — callers must transition through the intermediate states.
 */
@Internal
public final class TaskStateTransition {

    private TaskStateTransition() {
    }

    /** Canonical state name shared by both enums. */
    public enum StateName {
        CREATED, SCHEDULED, DEPLOYING, RUNNING, RECOVERING, CANCELING,
        COMPLETED, FAILED, CANCELED
    }

    private static final Map<StateName, Set<StateName>> LEGAL_TRANSITIONS;
    private static final Set<StateName> TERMINAL;

    static {
        Map<StateName, Set<StateName>> table = new HashMap<>();
        table.put(StateName.CREATED, setOf(
                StateName.SCHEDULED, StateName.CANCELING, StateName.FAILED));
        table.put(StateName.SCHEDULED, setOf(
                StateName.DEPLOYING, StateName.RECOVERING, StateName.CANCELING, StateName.FAILED));
        table.put(StateName.DEPLOYING, setOf(
                StateName.RUNNING, StateName.RECOVERING, StateName.CANCELING, StateName.FAILED));
        table.put(StateName.RUNNING, setOf(
                StateName.COMPLETED, StateName.RECOVERING, StateName.CANCELING, StateName.FAILED));
        table.put(StateName.RECOVERING, setOf(
                StateName.SCHEDULED, StateName.CANCELING, StateName.FAILED));
        table.put(StateName.CANCELING, setOf(StateName.CANCELED));
        // terminal states intentionally absent (no legal outgoing transitions)
        LEGAL_TRANSITIONS = Collections.unmodifiableMap(table);

        TERMINAL = EnumSet.of(StateName.COMPLETED, StateName.FAILED, StateName.CANCELED);
    }

    private static Set<StateName> setOf(StateName... names) {
        Set<StateName> s = new HashSet<>();
        for (StateName n : names) {
            s.add(n);
        }
        return Collections.unmodifiableSet(s);
    }

    public static boolean isTerminal(StateName state) {
        return TERMINAL.contains(state);
    }

    public static boolean isLegalTransition(StateName from, StateName to) {
        if (from == to) {
            return false;
        }
        Set<StateName> targets = LEGAL_TRANSITIONS.get(from);
        return targets != null && targets.contains(to);
    }

    /**
     * Validates the transition and throws {@link StreamException} if illegal.
     * Used by both {@link Task} and {@link SubtaskTask} via name-mapping overloads.
     */
    public static void validateTransition(StateName from, StateName to) {
        if (!isLegalTransition(from, to)) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_CURRENT_STATE, from.name())
                    .param(ARG_TARGET_STATE, to.name());
        }
    }

    // ---- Bridge methods for the two enum types ----

    public static void validateTransition(Task.State from, Task.State to) {
        validateTransition(StateName.valueOf(from.name()), StateName.valueOf(to.name()));
    }

    public static void validateTransition(SubtaskTask.State from, SubtaskTask.State to) {
        validateTransition(StateName.valueOf(from.name()), StateName.valueOf(to.name()));
    }

    public static boolean isLegalTransition(Task.State from, Task.State to) {
        return isLegalTransition(StateName.valueOf(from.name()), StateName.valueOf(to.name()));
    }

    public static boolean isLegalTransition(SubtaskTask.State from, SubtaskTask.State to) {
        return isLegalTransition(StateName.valueOf(from.name()), StateName.valueOf(to.name()));
    }

    public static boolean isTerminal(Task.State s) {
        return isTerminal(StateName.valueOf(s.name()));
    }

    public static boolean isTerminal(SubtaskTask.State s) {
        return isTerminal(StateName.valueOf(s.name()));
    }
}
