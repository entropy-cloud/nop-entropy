/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

/**
 * WI21 (§八 9): the operator-state keys under which timer-using operators
 * snapshot their timer state. Timer state is REQUIRED state for window and CEP
 * exactly-once — each consumer must snapshot under its declared key so the
 * checkpoint contract (and tests) can pin the wiring. Values are historical
 * literals preserved verbatim (compat with existing durable snapshots).
 */
public final class TimerStateKeys {

    /** WindowOperator's internal-timer snapshot key. */
    public static final String WINDOW_INTERNAL_TIMERS = "internal-timers";

    /** CepOperator's per-key event-time timer ledger key. */
    public static final String CEP_EVENT_TIME_TIMERS = "cep-event-time-timers";

    private TimerStateKeys() {
    }
}
