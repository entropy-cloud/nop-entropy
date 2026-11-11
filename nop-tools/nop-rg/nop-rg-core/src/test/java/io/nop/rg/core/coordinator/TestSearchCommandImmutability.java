package io.nop.rg.core.coordinator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * W-01 载体（plan 2564，C10-01 缺保护钉 P2-01）：SearchCommand patternBytes 急切编码 +
 * final 字段 JMM 安全发布。
 *
 * <p>腿清单（设计载体表 §二）：
 * <ul>
 *   <li>结构钉（确定性红证）：反射断言 {@code patternBytes} 字段为 {@code final}
 *       （修前惰性缓存字段非 final → 红0；钉的是终态形态）</li>
 *   <li>并发复用等价腿：两线程高次数并发 {@code patternBytes()}，断言返回字节恒等于
 *       {@code pattern.getBytes(UTF_8)}（急切修法后退化为普通等价测试——merge-adjudication
 *       §五.3 注记兑现）</li>
 *   <li>clone 防御腿：修改返回数组后再次调用不受影响（防御性克隆语义冻结面）</li>
 * </ul>
 */
public class TestSearchCommandImmutability {

    private SearchCommand command() {
        return new SearchCommand(java.nio.file.Path.of("/tmp"), "needle", SearchCoordinator.Strategy.LITERAL,
                false, List.of(), 0);
    }

    @Test
    public void patternBytesFieldIsFinalForSafePublication() throws Exception {
        // 结构钉（C10-01 确定性红证）：惰性 check-then-act 缓存字段非 final 存在 JMM
        // 未安全发布窗——终态形态必须为 final（与 FindCommand 全 final 对照形态归一）
        Field field = SearchCommand.class.getDeclaredField("patternBytes");
        assertTrue(Modifier.isFinal(field.getModifiers()),
                "patternBytes must be a final field (eager encoding, JMM safe publication); "
                        + "got modifiers=" + Modifier.toString(field.getModifiers()));
    }

    @Test
    public void concurrentReuseReturnsUtf8BytesEquivalently() throws Exception {
        // 含非 ASCII 字符（US-ASCII 编码替 UTF-8 的变异链 3 红证点）+ 多线程高次数并发
        SearchCommand command = new SearchCommand(java.nio.file.Path.of("/tmp"), "nëédle-needle",
                SearchCoordinator.Strategy.LITERAL, false, List.of(), 0);
        byte[] expected = "nëédle-needle".getBytes(StandardCharsets.UTF_8);
        int iterations = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger mismatches = new AtomicInteger();
        try {
            for (int t = 0; t < 2; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < iterations; i++) {
                            byte[] bytes = command.patternBytes();
                            if (!java.util.Arrays.equals(bytes, expected)) {
                                mismatches.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
        assertEquals(0, mismatches.get(), "并发 patternBytes() 必须恒等于 UTF-8 编码");
    }

    @Test
    public void returnedArrayIsDefensivelyCloned() {
        // 防御性克隆语义（javadoc 冻结面）：调用方修改返回数组不影响后续调用
        SearchCommand command = command();
        byte[] first = command.patternBytes();
        first[0] = 'X';
        byte[] second = command.patternBytes();
        assertArrayEquals("needle".getBytes(StandardCharsets.UTF_8), second,
                "修改返回数组不得影响后续调用（防御性克隆）");
        if (second[0] == 'X') {
            fail("clone defense broken");
        }
    }
}
