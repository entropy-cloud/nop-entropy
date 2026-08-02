/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.launch;

import java.util.HashMap;
import java.util.Map;

/**
 * Stage 42 Phase 1: shared configuration parsed by both {@link TaskManagerMain}
 * and {@link JobCoordinatorMain}. Args are passed as {@code key=value} tokens on
 * the command line; {@link #parse(String[])} materializes this record.
 *
 * <p>The shared backing store is H2 in {@code AUTO_SERVER=TRUE} mode (file-based,
 * multi-process accessible on the same machine). The same JDBC URL is passed to
 * every spawned JVM so they share one {@code JdbcClusterRegistry} + one
 * {@code PollingJdbcMessageService} (test infrastructure — production would use
 * {@code SysDaoMessageService} / Pulsar).
 */
public final class ClusterLaunchConfig {

    public static final String KEY_NODE_ID = "nodeId";
    public static final String KEY_JOB_ID = "jobId";
    public static final String KEY_JDBC_URL = "jdbcUrl";
    public static final String KEY_JDBC_USER = "jdbcUser";
    public static final String KEY_JDBC_PASSWORD = "jdbcPassword";
    public static final String KEY_TOPIC_NAMESPACE = "topicNamespace";
    public static final String KEY_CAPACITY = "capacity";
    public static final String KEY_CHECKPOINT_BASE_DIR = "checkpointBaseDir";
    public static final String KEY_FENCING_EPOCH = "fencingEpoch";
    public static final String KEY_COORDINATOR_ID = "coordinatorId";
    public static final String KEY_POLL_INTERVAL_MS = "pollIntervalMs";

    private final Map<String, String> raw;

    private ClusterLaunchConfig(Map<String, String> raw) {
        this.raw = raw;
    }

    public static ClusterLaunchConfig parse(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq <= 0 || eq == arg.length() - 1) {
                throw new IllegalArgumentException(
                        "Invalid argument format (expected key=value): " + arg);
            }
            String key = arg.substring(0, eq).trim();
            String value = arg.substring(eq + 1).trim();
            map.put(key, value);
        }
        return new ClusterLaunchConfig(map);
    }

    public String require(String key) {
        String v = raw.get(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required config: " + key + ". Provide as -D" + key + "=... or arg " + key + "=...");
        }
        return v;
    }

    public String get(String key, String defaultValue) {
        String v = raw.get(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    public int getInt(String key, int defaultValue) {
        String v = raw.get(key);
        if (v == null || v.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Config " + key + " must be an integer, got: " + v);
        }
    }

    public long getLong(String key, long defaultValue) {
        String v = raw.get(key);
        if (v == null || v.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Config " + key + " must be a long, got: " + v);
        }
    }

    public Map<String, String> raw() {
        return raw;
    }
}
