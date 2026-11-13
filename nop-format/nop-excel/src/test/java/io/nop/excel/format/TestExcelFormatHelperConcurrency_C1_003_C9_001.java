package io.nop.excel.format;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.text.Format;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.4-C1-003 + 5.4-C9-001（G5 单设计联合闭合）：格式注册表运行期可并发写读；
 * 创建型格式按格式串缓存且每线程独立实例（SimpleDateFormat/DecimalFormat 非线程安全，禁跨线程共享）。
 */
public class TestExcelFormatHelperConcurrency_C1_003_C9_001 {

    // formatCache 为 JVM 级静态状态：上界用例会把它填满，每个用例前清空以保持互不依赖
    @BeforeEach
    public void clearFormatCache() throws Exception {
        Field field = ExcelFormatHelper.class.getDeclaredField("formatCache");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(null)).clear();
    }

    @Test
    public void testFormatRegistryIsConcurrentMap() throws Exception {
        Field field = ExcelFormatHelper.class.getDeclaredField("formats");
        field.setAccessible(true);
        assertInstanceOf(ConcurrentHashMap.class, field.get(null));
    }

    @Test
    public void testConcurrentAddFormatAndGetFormat() throws Exception {
        Format custom = ExcelFormatHelper.getFormat("000-00-0000");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                int base = t * 500;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 500; i++) {
                        ExcelFormatHelper.addFormat("c1-003-custom-" + (base + i), custom);
                    }
                    return null;
                }));
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 500; i++) {
                        assertNotNull(ExcelFormatHelper.getFormat("000-00-0000"));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            for (int i = 0; i < 2000; i++) {
                assertSame(custom, ExcelFormatHelper.getFormat("c1-003-custom-" + i));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testCreatedDateFormatIsCachedWithinThread() {
        Format first = ExcelFormatHelper.getFormat("yyyy-mm-dd");
        assertNotNull(first);
        assertSame(first, ExcelFormatHelper.getFormat("yyyy-mm-dd"));
    }

    @Test
    public void testCreatedNumberFormatIsCachedWithinThread() {
        Format first = ExcelFormatHelper.getFormat("#,##0.00");
        assertNotNull(first);
        assertSame(first, ExcelFormatHelper.getFormat("#,##0.00"));
    }

    @Test
    public void testCreatedFormatIsNotSharedAcrossThreads() throws Exception {
        Format local = ExcelFormatHelper.getFormat("yyyy-mm-dd hh:mm:ss");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Format other = executor.submit(() -> ExcelFormatHelper.getFormat("yyyy-mm-dd hh:mm:ss"))
                    .get(10, TimeUnit.SECONDS);
            assertNotNull(other);
            assertNotSame(local, other);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testBuiltinFormatSingletonUnchanged() {
        assertSame(ExcelFormatHelper.getFormat("000-00-0000"), ExcelFormatHelper.getFormat("000-00-0000"));
    }

    @Test
    public void testUnconvertibleFormatStillReturnsNull() {
        assertNull(ExcelFormatHelper.getFormat("no-placeholders"));
        assertNull(ExcelFormatHelper.getFormat("no-placeholders"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFormatCacheIsBounded() throws Exception {
        Field field = ExcelFormatHelper.class.getDeclaredField("formatCache");
        field.setAccessible(true);
        Map<String, ?> cache = (Map<String, ?>) field.get(null);
        for (int i = 0; i < 400; i++) {
            ExcelFormatHelper.getFormat("0." + "0".repeat(i % 20) + "#".repeat(i / 20 + 1));
        }
        Field limit = ExcelFormatHelper.class.getDeclaredField("MAX_CACHED_FORMAT_PATTERNS");
        limit.setAccessible(true);
        assertTrue(cache.size() <= limit.getInt(null), "format cache exceeded bound: " + cache.size());
    }
}
