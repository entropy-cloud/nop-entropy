/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

/**
 * Maturity grade of a registered connector endpoint (WI4 / plan 2329, five-engine
 * comparison P0-3: SeaTunnel quality-grading analog).
 *
 * <p><strong>Grading criterion (semantic strength, applied to the declared
 * delivery/recovery semantics — not to test coverage)</strong>:
 * <ul>
 *   <li>{@link #PRODUCTION} — the declared semantics are fulfilled by the platform
 *       engine itself (checkpoint cursor / offset-in-checkpoint / two-phase commit).</li>
 *   <li>{@link #EXPERIMENTAL} — {@code recovery=NONE} or the declared semantics are
 *       carried by an external backend (e.g. the message family: at-least-once is
 *       the backing {@code IMessageService} backend's contract; the local backend is
 *       best-effort).</li>
 * </ul>
 *
 * <p>The descriptor default is {@link #EXPERIMENTAL}: {@code PRODUCTION} is an explicit
 * opt-in so an ungraded third-party factory cannot silently claim production grade.
 * The grade is surfaced by {@link StreamConnectorCatalog#renderListing()}, pinned per
 * built-in factory by the registration discovery test, and reported as a non-blocking
 * warning by conf-validate's connector validation mode.
 */
public enum ConnectorMaturity {
    EXPERIMENTAL,
    PRODUCTION
}
