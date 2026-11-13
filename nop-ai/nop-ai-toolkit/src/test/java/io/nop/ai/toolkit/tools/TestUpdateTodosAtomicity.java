package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.2-C1-004（plan 2557 裁定 B，行为钉）：并发 clear+write 终态一致——
 * {@link UpdateTodosExecutor} 的 clear/write 已收敛为
 * {@code todoLists.compute(listKey, ...)} 单次原子决策（getOrDefault→put
 * 丢单更新面消除），hammer 下任意可观测态只能是 {空, 单一写入方完整列表}。
 * <p>Scope note：会话条目清理面 Deferred（toolkit 无 session-lifecycle seam，
 * R1-Major-1）——本钉只覆盖原子性。
 */
public class TestUpdateTodosAtomicity {

    private static final java.util.concurrent.atomic.AtomicInteger CALL_IDS =
            new java.util.concurrent.atomic.AtomicInteger();

    private static AiToolCall writeCall(String idAttr, String idPrefix) {
        XNode node = XNode.make("update-todos");
        node.setAttr("id", String.valueOf(CALL_IDS.incrementAndGet()));
        node.setAttr("action", "write");
        XNode todos = node.makeChild("todos");
        for (int i = 1; i <= 3; i++) {
            // 注意：makeChild 会按 tag 去重（返回既有子节点），同一 tag 的多个 todo
            // 必须用 addChild 追加
            XNode todo = todos.addChild("todo");
            todo.setAttr("id", idPrefix + i);
            todo.setAttr("content", idPrefix + "-content-" + i);
            todo.setAttr("status", "pending");
            todo.setAttr("priority", "high");
        }
        return AiToolCall.fromNode(node);
    }

    private static AiToolCall clearCall(String idAttr) {
        XNode node = XNode.make("update-todos");
        node.setAttr("id", String.valueOf(CALL_IDS.incrementAndGet()));
        node.setAttr("action", "write");
        node.makeChild("todos");
        return AiToolCall.fromNode(node);
    }

    private static AiToolCall readCall(String idAttr) {
        XNode node = XNode.make("update-todos");
        node.setAttr("id", String.valueOf(CALL_IDS.incrementAndGet()));
        node.setAttr("action", "read");
        return AiToolCall.fromNode(node);
    }

    private static AiToolCallResult exec(UpdateTodosExecutor executor, AiToolCall call, String session) {
        return executor.executeAsync(call, new UpdateTodosExecutorTest.MockContext(session))
                .toCompletableFuture().join();
    }

    @Test
    void concurrentClearAndWriteTerminalStateIsConsistent() throws Exception {
        UpdateTodosExecutor executor = new UpdateTodosExecutor();
        String session = "s-atomicity";
        int iterations = 200;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<String> violation = new AtomicReference<>();

            Runnable writerA = () -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        exec(executor, writeCall("w" + i, "a"), session);
                    }
                } catch (Exception e) {
                    violation.compareAndSet(null, "writerA: " + e);
                }
            };
            Runnable writerB = () -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        exec(executor, writeCall("c" + i, "b"), session);
                    }
                } catch (Exception e) {
                    violation.compareAndSet(null, "writerB: " + e);
                }
            };
            Runnable clearer = () -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        exec(executor, clearCall("x" + i), session);
                    }
                } catch (Exception e) {
                    violation.compareAndSet(null, "clearer: " + e);
                }
            };
            Runnable reader = () -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        AiToolCallResult r = exec(executor, readCall("r" + i), session);
                        String body = r.getOutput() == null ? "<null output>" : r.getOutput().getBody();
                        // 一致性钉：任意可观测中间态必须是完整列表（3 条同一写入方）或空。
                        // 注意：read 呈现的是 content（非 id），用 content 前缀判归属
                        if (body.contains("3 items")) {
                            boolean allA = body.contains("a-content-1") && body.contains("a-content-2")
                                    && body.contains("a-content-3") && !body.contains("b-content-");
                            boolean allB = body.contains("b-content-1") && body.contains("b-content-2")
                                    && body.contains("b-content-3") && !body.contains("a-content-");
                            if (!allA && !allB) {
                                violation.compareAndSet(null, "mixed list: " + body);
                            }
                        } else if (!body.contains("0 items")) {
                            violation.compareAndSet(null, "partial list: " + body);
                        }
                    }
                } catch (Exception e) {
                    violation.compareAndSet(null, "reader: " + e);
                }
            };

            for (Runnable job : List.of(writerA, writerB, clearer, reader)) {
                pool.submit(job);
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS), "hammer must finish");
            assertFalse(violation.get() != null,
                    "no mixed/partial list state may be observed: " + violation.get());
        } finally {
            pool.shutdownNow();
        }

        // quiesce 后终态由最后一次写完全决定
        AiToolCall finalCall = writeCall("final", "final");
        org.junit.jupiter.api.Assertions.assertEquals(3,
                finalCall.getNode().childByTag("todos").getChildren().size(),
                "precondition: writeCall builds 3 todo children");
        assertEquals("success", exec(executor, finalCall, session).getStatus());
        AiToolCallResult finalRead = exec(executor, readCall("final-read"), session);
        String body = finalRead.getOutput().getBody();
        assertTrue(body.contains("final-content-1") && body.contains("final-content-3"),
                "final state = last write: " + body);
    }
}
