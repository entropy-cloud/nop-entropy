/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.core.lang.json.JsonTool;

/**
 * Item 15 (stability exercise): periodic sampling device. Every {@code intervalMs}
 * it pulls one sample from the {@link SampleSources} (JC ops /metrics via HTTP +
 * shared-DB queue depth + durable checkpoint epoch + retained manifest count +
 * sink-output progress + process liveness/CPU) and appends it as one JSON line to
 * {@code <samplesDir>/samples.jsonl}. The series is the drill's evidence archive
 * (Phase 1 observation-mode adjudication: direct JC-side metrics + storage/output/
 * shared-DB observables; TM-side io meters are NOT transportable cross-JVM —
 * recorded gap, not silently assumed).
 */
public final class ExerciseSampler implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ExerciseSampler.class);

    /**
     * One sampling probe. Implementations must be cheap (&lt; interval) and must
     * surface failures via the returned values (e.g. -1 / empty map), never throw.
     */
    public interface SampleSources {
        Map<String, Double> fetchMetrics();

        long fetchQueueDepth();

        long fetchDurableEpoch();

        long fetchRetainedManifestCount();

        long fetchOutputProgress();

        Map<String, Boolean> fetchAlive();
    }

    /** In-memory view of one sampled tick (also serialized to the JSONL file). */
    public static final class SampleRecord {
        public long wallMs;
        public Map<String, Double> metrics;
        public long queueDepth;
        public long durableEpoch;
        public long retainedManifests;
        public long outputProgress;
        public Map<String, Boolean> alive;
        public long stallMs;
        public String note;

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("wallMs", wallMs);
            m.put("metrics", metrics);
            m.put("queueDepth", queueDepth);
            m.put("durableEpoch", durableEpoch);
            m.put("retainedManifests", retainedManifests);
            m.put("outputProgress", outputProgress);
            m.put("alive", alive);
            m.put("stallMs", stallMs);
            if (note != null && !note.isBlank()) {
                m.put("note", note);
            }
            return m;
        }
    }

    private final Path samplesFile;
    private final long intervalMs;
    private final SampleSources sources;
    private final ScheduledExecutorService executor;
    private final List<SampleRecord> records = new ArrayList<>();
    private final long startWallMs = System.currentTimeMillis();
    private final AtomicLong lastProgressWallMs = new AtomicLong(System.currentTimeMillis());
    private volatile long lastEpoch = -1L;
    private volatile long lastOutputProgress = -1L;
    private volatile boolean failed;

    public ExerciseSampler(Path samplesFile, long intervalMs, SampleSources sources) {
        if (intervalMs < 100L) {
            throw new IllegalArgumentException("sample intervalMs must be >= 100 (got " + intervalMs + ")");
        }
        this.samplesFile = samplesFile;
        this.intervalMs = intervalMs;
        this.sources = sources;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "exercise-sampler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() throws IOException {
        Files.createDirectories(samplesFile.getParent());
        Files.writeString(samplesFile, "", StandardCharsets.UTF_8);
        executor.scheduleAtFixedRate(this::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void tick() {
        try {
            SampleRecord record = new SampleRecord();
            record.wallMs = System.currentTimeMillis();
            record.metrics = sources.fetchMetrics();
            record.queueDepth = sources.fetchQueueDepth();
            record.durableEpoch = sources.fetchDurableEpoch();
            record.retainedManifests = sources.fetchRetainedManifestCount();
            record.outputProgress = sources.fetchOutputProgress();
            record.alive = sources.fetchAlive();

            boolean progressed = false;
            if (lastEpoch == -1L || record.durableEpoch > lastEpoch) {
                lastEpoch = record.durableEpoch;
                progressed = true;
            }
            if (lastOutputProgress == -1L || record.outputProgress > lastOutputProgress) {
                lastOutputProgress = record.outputProgress;
                progressed = true;
            }
            if (progressed) {
                lastProgressWallMs.set(record.wallMs);
            }
            record.stallMs = record.wallMs - lastProgressWallMs.get();

            records.add(record);
            Files.writeString(samplesFile, JsonTool.serialize(record.toJson(), false) + "\n",
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable t) {
            failed = true;
            LOG.error("exercise sampler tick failed; stopping sampler", t);
            throw new IllegalStateException("exercise sampler tick failed: " + t, t);
        }
    }

    public synchronized List<SampleRecord> records() {
        return new ArrayList<>(records);
    }

    public boolean failed() {
        return failed;
    }

    public long getStartWallMs() {
        return startWallMs;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    // ----------------------------------------------------------------
    // Series analysis (pass/fail criteria, unit-tested)
    // ----------------------------------------------------------------

    /**
     * Max wall gap between consecutive durable-epoch advances (checkpoint progress
     * continuity). The trailing segment after the LAST advance is excluded —
     * awaiting convergence after the final commit is legitimate idle, not a stall.
     */
    public static long maxEpochAdvanceGapMs(List<SampleRecord> records) {
        long lastAdvanceWallMs = -1L;
        long maxGap = 0L;
        long prevEpoch = Long.MIN_VALUE;
        for (SampleRecord r : records) {
            if (r.durableEpoch > prevEpoch) {
                if (lastAdvanceWallMs != -1L) {
                    maxGap = Math.max(maxGap, r.wallMs - lastAdvanceWallMs);
                }
                lastAdvanceWallMs = r.wallMs;
                prevEpoch = r.durableEpoch;
            }
        }
        return maxGap;
    }

    public static long epochAdvanceCount(List<SampleRecord> records) {
        long count = 0L;
        long prevEpoch = Long.MIN_VALUE;
        for (SampleRecord r : records) {
            if (r.durableEpoch > prevEpoch) {
                count++;
                prevEpoch = r.durableEpoch;
            }
        }
        return count;
    }

    /**
     * Queue-depth leak signal: the depth is STILL ACTIVELY GROWING at the end of
     * the series (the last few consecutive samples all increasing) AND ends above
     * {@code threshold}. A run that converges with a bounded residual plateau
     * (spurious full-pair-subscription deliveries — recorded as an observation /
     * item-28 evidence) does NOT match: growth that stopped is not unbounded growth.
     */
    public static boolean queueDepthUnboundedGrowth(List<SampleRecord> records, long threshold) {
        long last = -1L;
        for (int i = records.size() - 1; i >= 0; i--) {
            if (records.get(i).queueDepth >= 0L) {
                last = records.get(i).queueDepth;
                break;
            }
        }
        if (last <= threshold) {
            return false;
        }
        int consecutiveIncreases = 0;
        long prev = Long.MIN_VALUE;
        for (int i = records.size() - 1; i >= 0 && consecutiveIncreases < 4; i--) {
            long depth = records.get(i).queueDepth;
            if (depth < 0L) {
                continue;
            }
            if (prev != Long.MIN_VALUE) {
                if (depth < prev) {
                    consecutiveIncreases++;
                } else {
                    break;
                }
            }
            prev = depth;
        }
        return consecutiveIncreases >= 4;
    }

    /**
     * Splits a Prometheus TextFormat 0.0.4 exposition into a name → value map.
     * HELP/TYPE/comment lines are skipped; for counters the raw {@code _total}
     * name is kept AND a suffix-stripped alias is added so callers can look up
     * either spelling. Malformed lines throw (the endpoint output is well-formed;
     * silent skips would hide wiring regressions).
     */
    public static Map<String, Double> parsePrometheusText(String text) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int sep = line.lastIndexOf(' ');
            if (sep <= 0 || sep == line.length() - 1) {
                throw new IllegalStateException("malformed prometheus line: '" + rawLine + "'");
            }
            String identifier = line.substring(0, sep).trim();
            double value;
            try {
                value = Double.parseDouble(line.substring(sep + 1).trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("malformed prometheus value: '" + rawLine + "'", e);
            }
            String name = identifier.contains("{")
                    ? identifier.substring(0, identifier.indexOf('{'))
                    : identifier;
            values.put(identifier, value);
            values.putIfAbsent(name, value);
            if (name.endsWith("_total")) {
                values.putIfAbsent(name.substring(0, name.length() - "_total".length()), value);
            }
        }
        return values;
    }

    /** Rounded double rendering for summary maps. */
    public static double round3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
