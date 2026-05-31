package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.IndexStatsDTO;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestConcurrentIndexing extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Test
    void testDifferentIndexIdsCanRunInParallel() throws Exception {
        Path projectRoot = new File("src/test/resources/test-project/src/main/java").toPath();
        String path = projectRoot.toAbsolutePath().toString();

        int taskCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(taskCount);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        List<String> indexIds = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < taskCount; i++) {
            final String indexId = "concurrent-test-" + i;
            indexIds.add(indexId);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int count = codeIndexService.indexDirectory(indexId, path, "**/*.java");
                    if (count > 0) {
                        successCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All tasks should complete within timeout");
        assertEquals(0, errorCount.get(), "No errors during concurrent indexing");
        assertEquals(taskCount, successCount.get());

        for (String indexId : indexIds) {
            IndexStatsDTO stats = codeIndexService.getIndexStats(indexId);
            assertNotNull(stats);
            codeIndexService.deleteIndex(indexId);
        }
    }
}
