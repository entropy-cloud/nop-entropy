/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.nop.commons.metrics.MeterPrintConfig;
import io.nop.commons.metrics.MeterPrinter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Item 16 (P-REQ-4): periodic in-process metrics reporter — the "log" and
 * "file" sink types of the metrics configuration template. The "prometheus"
 * sink type is the ops HTTP server's {@code /metrics} endpoint (pull).
 *
 * <p>Targets:
 * <ul>
 *   <li>{@code stdout} — renders all {@code nop.stream.*} (and any other
 *       process) meters through the platform {@link MeterPrinter} into the
 *       process log once per interval.</li>
 *   <li>{@code file} — atomically rewrites the given file with the same
 *       rendered snapshot each interval (sidecar-friendly).</li>
 * </ul>
 */
public class StreamMetricsReporter {

    public static final String KEY_ENABLED = "nop.stream.metrics.log.enabled";
    public static final String KEY_INTERVAL_MS = "nop.stream.metrics.log.interval-ms";
    public static final String KEY_TARGET = "nop.stream.metrics.log.target";
    public static final String KEY_FILE = "nop.stream.metrics.log.file";

    private static final Logger LOG = LoggerFactory.getLogger(StreamMetricsReporter.class);

    private final long intervalMs;
    private final String target;
    private final Path filePath;

    private ScheduledExecutorService scheduler;

    public StreamMetricsReporter(long intervalMs, String target, Path filePath) {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("metrics reporter interval must be positive: " + intervalMs);
        }
        this.intervalMs = intervalMs;
        this.target = target == null ? "stdout" : target;
        this.filePath = filePath;
        if ("file".equals(this.target) && (filePath == null || filePath.toString().isBlank())) {
            throw new IllegalArgumentException(
                    "metrics reporter target=file requires a file path (see " + KEY_FILE + ")");
        }
    }

    public synchronized void start() {
        if (scheduler != null) {
            throw new IllegalStateException("StreamMetricsReporter already started");
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stream-metrics-reporter");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::report, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        LOG.info("StreamMetricsReporter started (target={}, intervalMs={})", target, intervalMs);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            awaitTermination(scheduler);
            scheduler = null;
        }
    }

    private void awaitTermination(ScheduledExecutorService executor) {
        boolean terminated = false;
        try {
            terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!terminated) {
            LOG.warn("StreamMetricsReporter scheduler did not terminate within timeout");
        }
    }

    public boolean isRunning() {
        return scheduler != null;
    }

    void report() {
        try {
            String text = MeterPrinter.scrape(
                    io.nop.stream.core.metrics.StreamMetricsRegistries.registry(),
                    new MeterPrintConfig());
            if ("file".equals(target)) {
                writeSnapshot(text);
            } else {
                LOG.info("nop-stream metrics snapshot:\n{}", text);
            }
        } catch (Exception e) {
            LOG.warn("StreamMetricsReporter failed to report metrics: {}", e.toString());
        }
    }

    private void writeSnapshot(String text) throws IOException {
        Path parent = filePath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, filePath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
