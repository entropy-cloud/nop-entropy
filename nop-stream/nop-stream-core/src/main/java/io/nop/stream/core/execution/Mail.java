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
 * A single unit of control-plane work delivered to a task thread's {@link TaskMailbox}.
 *
 * <p>A mail wraps an action (closure) together with a {@link Priority} that determines
 * ordering when multiple mails are pending. Control mails (checkpoint triggers, cancel
 * signals) are always drained before normal mails.
 *
 * <p>Mails are the cooperative control channel for a task thread: they replace the
 * cross-thread handoff patterns (cap-1 queues + {@code Thread.interrupt()}) that were
 * previously used for checkpoint triggering and abort. See the mailbox design chapter in
 * {@code ai-dev/design/nop-stream/} for the ownership contract and the three usage sites.
 *
 * <p>Mails are single-consumer: only the owning task thread is permitted to call
 * {@link #run()}. Producers (barrier-injector thread, abort handler thread) only call
 * {@link TaskMailbox#put(Mail)}.
 */
@Internal
public final class Mail {

    /**
     * Priority of a mail. {@link #CONTROL} mails are drained before {@link #NORMAL} mails
     * whenever both are pending in the same {@link TaskMailbox}.
     */
    public enum Priority {
        CONTROL,
        NORMAL
    }

    private final Runnable action;
    private final Priority priority;
    private final String description;

    /**
     * Builds a control-priority mail. This is the recommended factory for checkpoint
     * triggers and cancel signals.
     *
     * @param action      the action to execute on the task thread; must not be null
     * @param description human-readable description used for diagnostics; must not be null
     */
    public static Mail control(Runnable action, String description) {
        return new Mail(action, Priority.CONTROL, description);
    }

    /**
     * Builds a normal-priority mail.
     *
     * @param action      the action to execute on the task thread; must not be null
     * @param description human-readable description used for diagnostics; must not be null
     */
    public static Mail normal(Runnable action, String description) {
        return new Mail(action, Priority.NORMAL, description);
    }

    /**
     * @param action      the action to execute on the task thread; must not be null
     * @param priority    mail priority; must not be null
     * @param description human-readable description used for diagnostics; must not be null
     */
    public Mail(Runnable action, Priority priority, String description) {
        if (action == null) {
            throw new IllegalArgumentException("Mail action must not be null");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Mail priority must not be null");
        }
        if (description == null) {
            throw new IllegalArgumentException("Mail description must not be null");
        }
        this.action = action;
        this.priority = priority;
        this.description = description;
    }

    /**
     * Executes the wrapped action on the calling (task) thread.
     */
    public void run() {
        action.run();
    }

    public Priority getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "Mail{priority=" + priority + ", description='" + description + "'}";
    }
}
