/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.connector.MessageSourceConnectorFactory;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.testsupport.TestAwait;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 plan 2343 Phase 2: the message factory → FLIP-27 source combination end to end —
 * the connector factory produces the {@code MessageSource}, the engine runs it through
 * {@code SourceReaderOperator}, and messages published on a {@link LocalMessageService}
 * topic arrive at the sink (push → poll bridge over the real pipeline). The job is
 * UNBOUNDED: the terminating event is the injected sink failure after the expected
 * message count.
 */
public class TestMessageSourceE2E {

    private static final int MESSAGE_COUNT = 20;

    @Test
    void factoryMessageSourceDeliversPublishedMessagesEndToEnd() throws Exception {
        LocalMessageService service = new LocalMessageService();
        MessageSourceConnectorFactory factory = new MessageSourceConnectorFactory();
        Source<String, ?, ?> source = (Source<String, ?, ?>) factory.createSource(
                new StreamConnectorConfig("message",
                        Map.of("topic", "e2e-msg-topic", "messageService", service)));

        CopyOnWriteArrayList<String> collected = new CopyOnWriteArrayList<>();
        AtomicInteger failures = new AtomicInteger();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        // the supervisor (restart budget → terminal job failure) lives in the checkpoint
        // engine path — without it a failed UNBOUNDED job would hang execute() forever
        env.enableCheckpointing(200L);
        env.getCheckpointConfig().setJobId("message-source-e2e");
        env.getCheckpointConfig().setCheckpointTimeout(5000L);
        env.getCheckpointConfig().setMinPause(100L);
        env.addSource(source, "message-src").sink(new SinkFunction<String>() {
            @Override
            public void consume(String value) {
                // records racing the terminal failure are dropped (delivery proof only
                // needs the MESSAGE_COUNT distinct values)
                if (collected.size() < MESSAGE_COUNT) {
                    collected.add(value);
                }
                if (collected.size() >= MESSAGE_COUNT && failures.incrementAndGet() == 1) {
                    throw new IllegalStateException("terminal stop after " + MESSAGE_COUNT + " messages");
                }
            }
        });

        Thread jobThread = new Thread(() -> {
            try {
                env.execute("message-source-e2e");
            } catch (Exception e) {
                // expected: the injected terminal failure (or supervisor stop)
            }
        }, "message-e2e-job");
        jobThread.start();

        // publish while the job is live. The subscription is established asynchronously
        // by the reader's runner thread, and LocalMessageService drops messages with no
        // consumer — so the loop republishes the not-yet-collected messages until the
        // expected set is complete (each message is in flight at most once, hence no
        // duplicates by construction).
        // 时间确定化（plan 2510）：重发节奏改由条件等待轮询承载（每次条件求值补发
        // 尚未收集到的消息），确定性条件 = 收集齐全；上限仅挂死保护。
        TestAwait.until("every published message collected (republish on each evaluation)",
                () -> {
                    for (int i = 1; i <= MESSAGE_COUNT; i++) {
                        String candidate = "msg-" + i;
                        if (!collected.contains(candidate)) {
                            service.send("e2e-msg-topic", candidate);
                        }
                    }
                    return collected.size() >= MESSAGE_COUNT;
                }, 30_000L);
        jobThread.join(30000);
        assertTrue(!jobThread.isAlive(), "the job must terminate after the injected failure");

        // every published message arrived exactly once (set equality — delivery order
        // across the push/pull bridge is not ordered by publication)
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= MESSAGE_COUNT; i++) {
            expected.add("msg-" + i);
        }
        List<String> sorted = new ArrayList<>(collected);
        sorted.sort(null);
        List<String> expectedSorted = new ArrayList<>(expected);
        expectedSorted.sort(null);
        assertEquals(expectedSorted, sorted,
                "every published message must arrive exactly once");
    }
}
