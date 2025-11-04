package io.nop.commons.text.formatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.DecimalFormat;
import java.text.Format;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FastIntegerFormatter 测试")
class FastIntegerFormatterTest {

    /**
     * 提供补零格式 ("000...") 的测试用例
     */
    static Stream<Arguments> paddingFormatTestCases() {
        String pattern9 = "000000000";
        String pattern3 = "000";
        return Stream.of(
            // --- 使用 "000000000" 模式 ---
            Arguments.of(pattern9, 123L, "正数，长度小于宽度"),
            Arguments.of(pattern9, 0L, "零"),
            Arguments.of(pattern9, -45L, "负数，长度小于宽度"),
            Arguments.of(pattern9, 987654321L, "正数，长度等于宽度"),
            Arguments.of(pattern9, -123456789L, "负数，长度等于宽度"),
            Arguments.of(pattern9, 1234567890L, "正数，长度大于宽度"),
            Arguments.of(pattern9, -1234567890L, "负数，长度大于宽度"),
            Arguments.of(pattern9, Long.MAX_VALUE, "Long 最大值"),
            Arguments.of(pattern9, Long.MIN_VALUE, "Long 最小值"),
            // --- 使用 "000" 模式 ---
            Arguments.of(pattern3, 5L, "正数，长度小于宽度 (短模式)"),
            Arguments.of(pattern3, -5L, "负数，长度小于宽度 (短模式)"),
            Arguments.of(pattern3, 123L, "正数，长度等于宽度 (短模式)"),
            Arguments.of(pattern3, 1234L, "正数，长度大于宽度 (短模式)")
        );
    }

    @ParameterizedTest(name = "[{index}] 补零模式 \"{0}\" | 数字: {1} | 场景: {2}")
    @MethodSource("paddingFormatTestCases")
    @DisplayName("功能测试：补零格式")
    void testPaddingFormat_shouldMatchDecimalFormat(String pattern, Number number, String description) {
        // 预期结果来自标准的 DecimalFormat
        DecimalFormat expectedFormatter = new DecimalFormat(pattern);
        String expected = expectedFormatter.format(number);

        // 实际结果来自我们的 FastIntegerFormatter
        Format actualFormatter = FastIntegerFormatter.fromPattern(pattern);
        String actual = actualFormatter.format(number);
        
        // 断言两者结果一致
        assertEquals(expected, actual);
    }

    /**
     * 提供不补零格式 ("###...") 的测试用例
     */
    static Stream<Arguments> nonPaddingFormatTestCases() {
        String pattern10 = "##########";
        String pattern3 = "###";
        return Stream.of(
            // --- 使用 "##########" 模式 ---
            Arguments.of(pattern10, 123L, "正数"),
            Arguments.of(pattern10, 0L, "零"),
            Arguments.of(pattern10, -45L, "负数"),
            Arguments.of(pattern10, 987654321L, "长正数"),
            Arguments.of(pattern10, -1234567890L, "长负数"),
            Arguments.of(pattern10, Long.MAX_VALUE, "Long 最大值"),
            Arguments.of(pattern10, Long.MIN_VALUE, "Long 最小值"),
             // --- 使用 "###" 模式 ---
            Arguments.of(pattern3, 5L, "正数 (短模式)"),
            Arguments.of(pattern3, 1234L, "正数，长度大于模式宽度")
        );
    }

    @ParameterizedTest(name = "[{index}] 不补零模式 \"{0}\" | 数字: {1} | 场景: {2}")
    @MethodSource("nonPaddingFormatTestCases")
    @DisplayName("功能测试：不补零格式")
    void testNonPaddingFormat_shouldMatchDecimalFormat(String pattern, Number number, String description) {
        // 预期结果来自标准的 DecimalFormat
        DecimalFormat expectedFormatter = new DecimalFormat(pattern);
        String expected = expectedFormatter.format(number);

        // 实际结果来自我们的 FastIntegerFormatter
        Format actualFormatter = FastIntegerFormatter.fromPattern(pattern);
        String actual = actualFormatter.format(number);

        // 断言两者结果一致
        assertEquals(expected, actual);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "#0#0", "00#0"})
    @DisplayName("异常测试：无效的格式模式")
    void testInvalidPatterns_shouldThrowException(String invalidPattern) {
        assertThrows(IllegalArgumentException.class, () -> {
            FastIntegerFormatter.fromPattern(invalidPattern);
        }, "模式 '" + invalidPattern + "' 应该抛出 IllegalArgumentException");
    }

    @Test
    @DisplayName("异常测试：格式化非 Number 对象")
    void testFormatNonNumberObject_shouldThrowException() {
        Format formatter = FastIntegerFormatter.fromPattern("000");
        assertThrows(IllegalArgumentException.class, () -> {
            formatter.format("not a number");
        });
    }
    
    @Test
    @DisplayName("线程安全测试")
    void testThreadSafety() throws InterruptedException {
        // 共享同一个 formatter 实例
        final Format formatter = FastIntegerFormatter.fromPattern("000000000");
        
        final int numThreads = 100;
        final int iterationsPerThread = 1000;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch latch = new CountDownLatch(numThreads);
        // 使用一个线程安全的列表来收集结果，或者仅用作标志
        final List<Boolean> results = new ArrayList<>(numThreads);

        long[] testNumbers = {1L, 123L, 45678L, 987654321L, -1L, -123L, -45678L};
        String[] expectedStrings = {"000000001", "000000123", "000045678", "987654321", "-000000001", "-000000123", "-000045678"};

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    boolean success = true;
                    for (int j = 0; j < iterationsPerThread; j++) {
                        for(int k=0; k<testNumbers.length; k++){
                            String actual = formatter.format(testNumbers[k]);
                            if (!expectedStrings[k].equals(actual)) {
                                System.err.printf("线程 %s 失败: 数字 %d, 期望 '%s', 得到 '%s'%n",
                                        Thread.currentThread().getName(), testNumbers[k], expectedStrings[k], actual);
                                success = false;
                                break;
                            }
                        }
                        if(!success) break;
                    }
                    synchronized (results) {
                        results.add(success);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 等待所有线程完成
        executor.shutdown();

        assertEquals(numThreads, results.size(), "所有线程都应该已完成");
        assertTrue(results.stream().allMatch(Boolean::booleanValue), "一个或多个线程产生了不正确的结果，表明存在线程安全问题");
    }
}