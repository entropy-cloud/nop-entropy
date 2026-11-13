/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import io.nop.stream.runtime.metrics.RuntimeMetrics;
import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import io.nop.stream.core.util.NopStreamThreadFactory;
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
    /** ST-6 裁定（2026-09-13，审计 plan 357）：保留引擎自建调度线程——自包含运行时，指标周期上报随引擎生命周期；nop-job 外置触发为 successor 候选。 */


    public static final String KEY_ENABLED = "nop.stream.metrics.log.enabled";
    public static final String KEY_INTERVAL_MS = "nop.stream.metrics.log.interval-ms";
    public static final String KEY_TARGET = "nop.stream.metrics.log.target";
    public static final String KEY_FILE = "nop.stream.metrics.log.file";

    private static final Logger LOG = LoggerFactory.getLogger(StreamMetricsReporter.class);

    private final long intervalMs;
    private final String target;
    private final Path filePath;

    // K1-补遗 (CRA 8.41 R4-01, ruling E.1 coverage row 11): interval-cadence
    // report-failure WARN suppression — a persistently failing scrape/file write
    // would otherwise emit one WARN per interval forever. Per-site instance.
    private final io.nop.stream.runtime.support.PeriodicFailureSuppressor reportFailureSuppressor =
            new io.nop.stream.runtime.support.PeriodicFailureSuppressor(
                    "stream-metrics-reporter.report-failed", 30_000L);

    private ScheduledExecutorService scheduler;

    public StreamMetricsReporter(long intervalMs, String target, Path filePath) {
        if (intervalMs <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "metrics reporter interval must be positive: " + intervalMs);
        }
        this.intervalMs = intervalMs;
        this.target = target == null ? "stdout" : target;
        this.filePath = filePath;
        if ("file".equals(this.target) && (filePath == null || filePath.toString().isBlank())) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "metrics reporter target=file requires a file path (see " + KEY_FILE + ")");
        }
    }

    public synchronized void start() {
        if (scheduler != null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "StreamMetricsReporter already started");
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                NopStreamThreadFactory.named("stream-metrics-reporter"));
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
                    io.nop.stream.runtime.metrics.RuntimeMetrics.registry(),
                    new MeterPrintConfig());
            if ("file".equals(target)) {
                writeSnapshot(text);
            } else {
                LOG.info("nop-stream metrics snapshot:\n{}", text);
            }
            // K1-补遗 recovery (CRA 8.41 R4-01, ruling E.1 row 11): a report that
            // did not throw clears the window.
            if (reportFailureSuppressor.success()
                    == io.nop.stream.runtime.support.PeriodicFailureSuppressor.Action.LOG_CLEARED) {
                LOG.info("StreamMetricsReporter report recovered (failure window cleared)");
            }
        } catch (Exception e) {
            // K1-补遗 (ruling E.1 coverage row 11 — the plan 勘误 3 "zero hits"
            // note was overturned): three-state suppression on the interval-cadence
            // report-failure WARN; the first entry keeps the original shape.
            io.nop.stream.runtime.support.PeriodicFailureSuppressor.Action a =
                    reportFailureSuppressor.failure(e);
            if (a == io.nop.stream.runtime.support.PeriodicFailureSuppressor.Action.LOG_FULL) {
                LOG.warn("StreamMetricsReporter failed to report metrics: {}", e.toString());
            } else if (a == io.nop.stream.runtime.support.PeriodicFailureSuppressor.Action.LOG_AGGREGATE) {
                LOG.warn("StreamMetricsReporter report failures — {} suppressed in the last {}ms window; "
                                + "last: {}",
                        reportFailureSuppressor.getLastWindowSuppressedCount(),
                        reportFailureSuppressor.getWindowMs(), reportFailureSuppressor.getLastSummary());
            }
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
