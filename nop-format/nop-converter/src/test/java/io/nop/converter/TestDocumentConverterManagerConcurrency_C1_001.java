package io.nop.converter;

import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 5.4-C1-001：外层 ConcurrentHashMap 的内层集合同样必须是并发容器，运行期注册与查找并发安全。
 */
public class TestDocumentConverterManagerConcurrency_C1_001 {

    static class DummyConverter implements IDocumentConverter {
        @Override
        public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
            return "";
        }

        @Override
        public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out,
                                    DocumentConvertOptions options) {
            throw new UnsupportedOperationException("not used in concurrency test");
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object target, String name) throws Exception {
        Field f = DocumentConverterManager.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    @Test
    public void testInnerContainersAreConcurrent() throws Exception {
        DocumentConverterManager manager = new DocumentConverterManager();
        manager.registerConverter("c1a", "c1b", new DummyConverter());

        Map<String, Map<String, IDocumentConverter>> converters = field(manager, "converters");
        Map<String, ?> fromFileTypeMap = field(manager, "fromFileTypeMap");
        assertInstanceOf(ConcurrentHashMap.class, converters.get("c1a"));
        assertInstanceOf(ConcurrentSkipListSet.class, fromFileTypeMap.get("c1b"));
    }

    // 并发注册 from->to 与链式查找交错时，中间类型集合会包含源类型本身：必须跳过而不是触发 from==to 守卫异常
    @Test
    public void testChainedLookupSkipsSelfLoopIntermediate() throws Exception {
        DocumentConverterManager manager = new DocumentConverterManager();
        manager.registerConverter("src", "dst", new DummyConverter());
        Method find = DocumentConverterManager.class.getDeclaredMethod("findChainedConverter",
                String.class, String.class, boolean.class);
        find.setAccessible(true);
        assertNull(find.invoke(manager, "src", "dst", false));
    }

    @Test
    public void testConcurrentRegisterAndLookup() throws Exception {
        DocumentConverterManager manager = new DocumentConverterManager();
        IDocumentConverter converter = new DummyConverter();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                int base = t * 300;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 300; i++) {
                        manager.registerConverter("src", "dst" + (base + i), converter);
                        manager.registerConverter("mid" + (base + i), "dst" + (base + i), converter);
                    }
                    return null;
                }));
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 300; i++) {
                        manager.getConverter("src", "dst" + i, true);
                        manager.getToFileTypes("src", false);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            for (int i = 0; i < 1200; i++) {
                assertSame(converter, manager.getConverter("src", "dst" + i, false));
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
