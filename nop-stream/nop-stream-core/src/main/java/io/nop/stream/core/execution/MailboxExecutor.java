/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.api.core.annotations.core.Internal;

/**
 * Holds the per-task {@link TaskMailbox} together with the cooperative cancel flag, and
 * provides helpers to drain pending mails on the owning task thread.
 *
 * <p>One {@code MailboxExecutor} is owned by each {@link StreamTaskInvokable} (and thus by
 * each {@link SubtaskTask}). It is the control-plane anchor for a task thread:
 * <ul>
 *   <li>The barrier-injector thread and the abort handler thread deliver checkpoint-trigger
 *       and cancel mails via {@link #getMailbox()}.{@link TaskMailbox#put(Mail) put(...)}.</li>
 *   <li>The owning task thread drains pending mails at well-defined safe points:
 *       the top of {@code processInputGate} (MIDDLE/SINK) and the SOURCE
 *       {@code SourceContext.collect()} emission point.</li>
 *   <li>The cooperative cancel flag is set by the abort path (after unblocking the data
 *       read with an interrupt) and polled at the top of the task's main loop so the task
 *       exits gracefully instead of relying solely on {@link InterruptedException}.</li>
 * </ul>
 *
 * <p>Design note (why a separate executor and not just the mailbox): the cancel flag is a
 * task-lifetime boolean that must be observable from the task thread independently of
 * whether a mail is currently pending. Coupling it with the mailbox keeps the control
 * plane in a single object reachable from the {@link StreamTaskInvokable}.
 */
@Internal
public final class MailboxExecutor {

    private final TaskMailbox mailbox;
    private volatile boolean cancelled = false;

    public MailboxExecutor() {
        this(new TaskMailbox());
    }

    public MailboxExecutor(TaskMailbox mailbox) {
        if (mailbox == null) {
            throw new IllegalArgumentException("TaskMailbox must not be null");
        }
        this.mailbox = mailbox;
    }

    public TaskMailbox getMailbox() {
        return mailbox;
    }

    /**
     * @return true once the task has been cooperatively asked to cancel
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Marks the task as cancelled. The owning task thread observes this at the top of its
     * main loop and exits. This is the cooperative half of the abort path; the other half
     * is an interrupt that unblocks any blocking data read (see the abort design chapter).
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Drains and runs every currently-pending mail on the calling (task) thread, control
     * mails first. Used at the top of {@code processInputGate} (MIDDLE/SINK) and at the
     * SOURCE {@code collect()} emission point.
     *
     * @return {@code true} if the task has been cancelled (so the caller should exit its
     *         loop immediately after this call)
     */
    public boolean processAvailableMails() {
        mailbox.drainAndRun();
        return cancelled;
    }

    /**
     * Convenience: deliver a control-priority cancel mail and raise the cancel flag.
     * Intended for the abort handler so that a task blocked in a non-{@code collect()}
     * section still observes cancellation at its next mailbox drain.
     */
    public void signalCancel() {
        cancelled = true;
        mailbox.put(Mail.control(() -> {
        }, "cancel-flag-marker"));
    }

    /**
     * Standalone control-plane loop: drain mails until cancelled. Provided for future
     * processing-time timer wiring and for focused unit tests of the mailbox primitive.
     * The current task main loops (MIDDLE/SINK {@code processInputGate}, SOURCE
     * {@code run()}) integrate mailbox draining inline rather than delegating to this
     * loop, because they must interleave data-plane processing with control-plane drains.
     */
    public void runLoop() {
        while (!cancelled) {
            Mail mail = mailbox.poll();
            if (mail != null) {
                mail.run();
            } else {
                Thread.yield();
            }
        }
    }
}
