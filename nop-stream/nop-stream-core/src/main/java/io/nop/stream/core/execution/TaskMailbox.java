/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.concurrent.ConcurrentLinkedQueue;

import io.nop.api.core.annotations.core.Internal;

/**
 * Single-consumer, multi-producer mailbox for control-plane {@link Mail mails}.
 *
 * <p>One {@code TaskMailbox} is owned by exactly one task (one {@link StreamTaskInvokable}
 * and its {@link SubtaskTask}). Any thread may {@link #put(Mail)} (barrier-injector thread,
 * abort handler thread); only the owning task thread may {@link #poll()} or {@link #take()}.
 *
 * <p>Ordering contract:
 * <ul>
 *   <li>Within a priority class, FIFO order is preserved between a single producer and
 *       the consumer ( {@link ConcurrentLinkedQueue} per priority class).</li>
 *   <li>{@link Mail.Priority#CONTROL} mails are always drained before
 *       {@link Mail.Priority#NORMAL} mails whenever both are pending.</li>
 * </ul>
 *
 * <p>Non-blocking {@link #poll()} returns {@code null} immediately when no mail is pending.
 * Blocking {@link #take()} blocks until a mail is available or the mailbox is
 * {@link #close() closed}; after close, {@code take()} returns {@code null} so the consumer
 * loop can exit cooperatively.
 */
@Internal
public final class TaskMailbox {

    private final ConcurrentLinkedQueue<Mail> controlQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Mail> normalQueue = new ConcurrentLinkedQueue<>();

    private final Object lock = new Object();
    private volatile boolean closed = false;

    /**
     * Delivers a mail to the mailbox. Non-blocking, safe to call from any thread.
     *
     * <p>If the mailbox has been {@link #close() closed}, the mail is silently dropped
     * (the owning task is no longer consuming). A closed mailbox is a terminal state.
     *
     * @param mail the mail to deliver; must not be null
     */
    public void put(Mail mail) {
        if (mail == null) {
            throw new IllegalArgumentException("Mail must not be null");
        }
        if (closed) {
            // Mailbox is closed; the consumer has stopped or is stopping. Dropping the
            // mail is the cooperative behavior (e.g. abort handler delivering a cancel
            // mail to a task that has already finished).
            return;
        }
        if (mail.getPriority() == Mail.Priority.CONTROL) {
            controlQueue.add(mail);
        } else {
            normalQueue.add(mail);
        }
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    /**
     * Non-blocking poll. Returns the highest-priority pending mail, or {@code null} if
     * the mailbox is empty. Safe to call only from the owning task thread.
     *
     * @return the next mail, or {@code null} if none is pending
     */
    public Mail poll() {
        Mail m = controlQueue.poll();
        if (m != null) {
            return m;
        }
        return normalQueue.poll();
    }

    /**
     * Blocking take. Waits until a mail is available or the mailbox is closed.
     * After {@link #close()}, returns {@code null} so the consumer can exit.
     *
     * @return the next mail, or {@code null} if the mailbox was closed while waiting
     * @throws InterruptedException if the calling thread was interrupted while waiting
     */
    public Mail take() throws InterruptedException {
        while (true) {
            Mail m = poll();
            if (m != null) {
                return m;
            }
            if (closed) {
                return null;
            }
            synchronized (lock) {
                if (!controlQueue.isEmpty() || !normalQueue.isEmpty() || closed) {
                    continue;
                }
                lock.wait();
            }
        }
    }

    /**
     * Closes the mailbox. After close, {@link #take()} returns {@code null} and
     * {@link #put(Mail)} drops mails. Idempotent.
     */
    public void close() {
        closed = true;
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * @return true if no mail is pending in either priority class
     */
    public boolean isEmpty() {
        return controlQueue.isEmpty() && normalQueue.isEmpty();
    }

    /**
     * @return number of pending mails across both priority classes
     */
    public int size() {
        return controlQueue.size() + normalQueue.size();
    }

    /**
     * Drains and runs every currently-pending mail on the calling (task) thread.
     * Convenience for SOURCE emission points that must flush control mails before
     * emitting a record. Control mails are drained before normal mails.
     *
     * @return true if the mailbox is empty after draining
     */
    public boolean drainAndRun() {
        Mail m;
        while ((m = poll()) != null) {
            m.run();
        }
        return isEmpty();
    }
}
