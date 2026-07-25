package io.nop.stream.core.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for the mailbox primitives: {@link Mail}, {@link TaskMailbox},
 * {@link MailboxExecutor}. Covers delivery ordering, control-priority precedence,
 * close-then-take exit, multi-producer/single-consumer thread safety, and non-blocking
 * poll-empty behavior.
 */
class TestTaskMailbox {

    @Test
    void testPutAndPollPreservesFifoOrderWithinPriority() {
        TaskMailbox mailbox = new TaskMailbox();
        List<String> order = Collections.synchronizedList(new ArrayList<>());

        mailbox.put(Mail.normal(() -> order.add("n1"), "n1"));
        mailbox.put(Mail.normal(() -> order.add("n2"), "n2"));
        mailbox.put(Mail.normal(() -> order.add("n3"), "n3"));

        // Drain all
        Mail m;
        while ((m = mailbox.poll()) != null) {
            m.run();
        }

        assertEquals(3, order.size());
        assertEquals("n1", order.get(0));
        assertEquals("n2", order.get(1));
        assertEquals("n3", order.get(2));
        assertTrue(mailbox.isEmpty());
    }

    @Test
    void testControlMailHasPriorityOverNormalMail() {
        TaskMailbox mailbox = new TaskMailbox();
        List<String> order = Collections.synchronizedList(new ArrayList<>());

        // Interleave control and normal mails; control must be drained first.
        mailbox.put(Mail.normal(() -> order.add("normal-1"), "normal-1"));
        mailbox.put(Mail.control(() -> order.add("control-1"), "control-1"));
        mailbox.put(Mail.normal(() -> order.add("normal-2"), "normal-2"));
        mailbox.put(Mail.control(() -> order.add("control-2"), "control-2"));

        mailbox.drainAndRun();

        assertEquals(4, order.size());
        // Control mails run before any normal mail
        assertEquals("control-1", order.get(0));
        assertEquals("control-2", order.get(1));
        assertEquals("normal-1", order.get(2));
        assertEquals("normal-2", order.get(3));
    }

    @Test
    void testNonBlockingPollReturnsNullWhenEmpty() {
        TaskMailbox mailbox = new TaskMailbox();
        assertTrue(mailbox.isEmpty());
        assertNull(mailbox.poll(), "poll() on empty mailbox must not block");
        assertNull(mailbox.poll(), "repeated poll() must keep returning null immediately");
    }

    @Test
    void testTakeBlocksUntilMailDelivered() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        AtomicReference<String> received = new AtomicReference<>();
        CountDownLatch taken = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                Mail m = mailbox.take();
                if (m != null) {
                    received.set(m.getDescription());
                }
                taken.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "mailbox-consumer");
        consumer.start();

        // Give consumer a moment to enter take()
        Thread.sleep(50);
        assertEquals(1, taken.getCount(), "consumer must still be blocked while mailbox empty");

        mailbox.put(Mail.control(() -> {
        }, "delivered-mail"));

        assertTrue(taken.await(2, TimeUnit.SECONDS), "take() must return after a mail is delivered");
        assertEquals("delivered-mail", received.get());
        consumer.join(2000);
    }

    @Test
    void testTakeReturnsNullAfterClose() throws Exception {
        TaskMailbox mailbox = new TaskMailbox();
        AtomicReference<Mail> result = new AtomicReference<>();
        result.set(Mail.control(() -> {
        }, "sentinel")); // mark as "not yet returned"
        CountDownLatch returned = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                Mail m = mailbox.take();
                result.set(m);
                returned.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "mailbox-consumer-close");
        consumer.start();

        Thread.sleep(50);
        assertFalse(mailbox.isClosed());
        mailbox.close();
        assertTrue(mailbox.isClosed());

        assertTrue(returned.await(2, TimeUnit.SECONDS), "take() must return after close()");
        assertNull(result.get(), "take() must return null after close");
        consumer.join(2000);
    }

    @Test
    void testPutOnClosedMailboxIsDropped() {
        TaskMailbox mailbox = new TaskMailbox();
        mailbox.close();
        mailbox.put(Mail.control(() -> {
        }, "after-close"));
        // Mail is dropped, mailbox stays empty
        assertNull(mailbox.poll());
        assertTrue(mailbox.isEmpty());
    }

    @Test
    void testCloseIsIdempotent() {
        TaskMailbox mailbox = new TaskMailbox();
        mailbox.close();
        mailbox.close(); // must not throw
        assertTrue(mailbox.isClosed());
    }

    @Test
    @Timeout(10)
    void testMultiProducerSingleConsumerThreadSafety() throws Exception {
        final int producerCount = 4;
        final int mailsPerProducer = 500;
        TaskMailbox mailbox = new TaskMailbox();
        AtomicInteger consumed = new AtomicInteger(0);
        AtomicInteger controlConsumed = new AtomicInteger(0);
        AtomicInteger normalConsumed = new AtomicInteger(0);

        ExecutorService producers = Executors.newFixedThreadPool(producerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        for (int p = 0; p < producerCount; p++) {
            final int pid = p;
            futures.add(producers.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < mailsPerProducer; i++) {
                        // Alternate control/normal to exercise both queues concurrently
                        if (i % 2 == 0) {
                            mailbox.put(Mail.control(() -> {
                                consumed.incrementAndGet();
                                controlConsumed.incrementAndGet();
                            }, "ctrl-" + pid + "-" + i));
                        } else {
                            mailbox.put(Mail.normal(() -> {
                                consumed.incrementAndGet();
                                normalConsumed.incrementAndGet();
                            }, "norm-" + pid + "-" + i));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        // Single consumer drains until all expected mails have been consumed
        int expected = producerCount * mailsPerProducer;
        Thread consumer = new Thread(() -> {
            int seen = 0;
            while (seen < expected) {
                Mail m = mailbox.poll();
                if (m != null) {
                    m.run();
                    seen++;
                } else {
                    Thread.yield();
                }
            }
        }, "mailbox-single-consumer");

        start.countDown();
        consumer.start();

        // Wait for producers to finish enqueuing
        for (java.util.concurrent.Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        consumer.join(10000);

        assertEquals(expected, consumed.get(), "all delivered mails must be consumed exactly once");
        assertEquals(expected, controlConsumed.get() + normalConsumed.get());
        assertEquals(0, mailbox.size(), "mailbox must be empty after draining all mails");

        producers.shutdownNow();
    }

    @Test
    void testMailFactoryHelpers() {
        Mail control = Mail.control(() -> {
        }, "c");
        assertEquals(Mail.Priority.CONTROL, control.getPriority());
        assertEquals("c", control.getDescription());

        Mail normal = Mail.normal(() -> {
        }, "n");
        assertEquals(Mail.Priority.NORMAL, normal.getPriority());

        assertThrows(IllegalArgumentException.class, () -> new Mail(null, Mail.Priority.CONTROL, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Mail(() -> {
        }, null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Mail(() -> {
        }, Mail.Priority.CONTROL, null));
    }

    @Test
    void testMailRunExecutesAction() {
        AtomicInteger counter = new AtomicInteger(0);
        Mail mail = Mail.control(counter::incrementAndGet, "inc");
        mail.run();
        mail.run();
        assertEquals(2, counter.get());
    }
}
