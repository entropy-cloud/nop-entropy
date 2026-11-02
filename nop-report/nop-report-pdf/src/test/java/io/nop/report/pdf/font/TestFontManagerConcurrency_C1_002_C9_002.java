package io.nop.report.pdf.font;

import io.nop.autotest.junit.JunitBaseTestCase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.4-C1-002 + 5.4-C9-002（G4 单设计联合闭合）：别名注册表与默认字体并发安全；
 * 初始化完成后取字体不再经过实例锁，回退字体探测（字体文件 I/O）不持有实例锁。
 */
public class TestFontManagerConcurrency_C1_002_C9_002 extends JunitBaseTestCase {

    @Test
    public void testAliasRegistryIsConcurrentMap() throws Exception {
        Field field = FontManager.class.getDeclaredField("fontNameAliases");
        field.setAccessible(true);
        assertInstanceOf(ConcurrentHashMap.class, field.get(new FontManager()));
    }

    @Test
    public void testConcurrentAddAliasAndGetFont() throws Exception {
        FontManager fm = new FontManager();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try (PDDocument doc = new PDDocument()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                int base = t * 300;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 300; i++) {
                        fm.addFontAlias("c1-002-alias-" + (base + i), "Courier");
                    }
                    return null;
                }));
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 300; i++) {
                        assertNotNull(fm.getFont("Helvetica", false, false, doc));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            for (int i = 0; i < 1200; i++) {
                assertEquals("Courier", fm.getFont("c1-002-alias-" + i, false, false, doc).getName());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // 初始化完成后，另一线程持有实例锁期间取字体不得被阻塞（init 快路径无锁）
    @Test
    public void testGetFontDoesNotTakeInstanceLockAfterInit() throws Exception {
        FontManager fm = new FontManager();
        assertNotNull(fm.getFont("Helvetica", false, false, null));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            synchronized (fm) {
                Future<PDFont> future = executor.submit(() -> fm.getFont("Helvetica", false, false, null));
                assertNotNull(future.get(5, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // 回退字体探测与字体目录扫描使用专用锁，实例锁上不发生文件 I/O
    @Test
    public void testFallbackResolutionUsesDedicatedLocks() throws Exception {
        Method resolve = FontManager.class.getDeclaredMethod("resolveFallbackFont", PDDocument.class);
        assertFalse(Modifier.isSynchronized(resolve.getModifiers()),
                "resolveFallbackFont must not synchronize on the FontManager instance");

        Field fallbackLock = FontManager.class.getDeclaredField("fallbackLock");
        Field fontScanLock = FontManager.class.getDeclaredField("fontScanLock");
        assertTrue(Modifier.isFinal(fallbackLock.getModifiers()));
        assertTrue(Modifier.isFinal(fontScanLock.getModifiers()));

        assertTrue(Modifier.isVolatile(FontManager.class.getDeclaredField("inited").getModifiers()));
        assertTrue(Modifier.isVolatile(FontManager.class.getDeclaredField("defaultFont").getModifiers()));
    }

    @Test
    public void testDefaultFontAvailable() {
        assertNotNull(new FontManager().getDefaultFont());
    }
}
