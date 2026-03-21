/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Checkpoint 配置类。
 */
public class CheckpointConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_CHECKPOINT_INTERVAL = 60000L;
    public static final long DEFAULT_CHECKPOINT_TIMEOUT = 600000L;
    public static final long DEFAULT_MIN_PAUSE = 500L;
    public static final int DEFAULT_MAX_CONCURRENT_CHECKPOINTS = 1;
    public static final int DEFAULT_MAX_RETAINED_CHECKPOINTS = 5;

    private boolean checkpointEnabled = true;
    private long checkpointInterval = DEFAULT_CHECKPOINT_INTERVAL;
    private long checkpointTimeout = DEFAULT_CHECKPOINT_TIMEOUT;
    private long minPause = DEFAULT_MIN_PAUSE;
    private int maxConcurrentCheckpoints = DEFAULT_MAX_CONCURRENT_CHECKPOINTS;
    private int maxRetainedCheckpoints = DEFAULT_MAX_RETAINED_CHECKPOINTS;
    private String storageType = "local";
    private Map<String, String> storageConfig = new HashMap<>();

    public CheckpointConfig() {
    }

    public boolean isCheckpointEnabled() {
        return checkpointEnabled;
    }

    public void setCheckpointEnabled(boolean checkpointEnabled) {
        this.checkpointEnabled = checkpointEnabled;
    }

    public long getCheckpointInterval() {
        return checkpointInterval;
    }

    public void setCheckpointInterval(long checkpointInterval) {
        this.checkpointInterval = checkpointInterval;
    }

    public long getCheckpointTimeout() {
        return checkpointTimeout;
    }

    public void setCheckpointTimeout(long checkpointTimeout) {
        this.checkpointTimeout = checkpointTimeout;
    }

    public long getMinPause() {
        return minPause;
    }

    public void setMinPause(long minPause) {
        this.minPause = minPause;
    }

    public int getMaxConcurrentCheckpoints() {
        return maxConcurrentCheckpoints;
    }

    public void setMaxConcurrentCheckpoints(int maxConcurrentCheckpoints) {
        this.maxConcurrentCheckpoints = maxConcurrentCheckpoints;
    }

    public int getMaxRetainedCheckpoints() {
        return maxRetainedCheckpoints;
    }

    public void setMaxRetainedCheckpoints(int maxRetainedCheckpoints) {
        this.maxRetainedCheckpoints = maxRetainedCheckpoints;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public Map<String, String> getStorageConfig() {
        return storageConfig;
    }

    public void setStorageConfig(Map<String, String> storageConfig) {
        this.storageConfig = storageConfig != null ? storageConfig : new HashMap<>();
    }

    public void setStorageProperty(String key, String value) {
        storageConfig.put(key, value);
    }

    public String getStorageProperty(String key) {
        return storageConfig.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CheckpointConfig config = new CheckpointConfig();

        public Builder checkpointEnabled(boolean enabled) {
            config.setCheckpointEnabled(enabled);
            return this;
        }

        public Builder checkpointInterval(long interval) {
            config.setCheckpointInterval(interval);
            return this;
        }

        public Builder checkpointTimeout(long timeout) {
            config.setCheckpointTimeout(timeout);
            return this;
        }

        public Builder minPause(long minPause) {
            config.setMinPause(minPause);
            return this;
        }

        public Builder maxConcurrentCheckpoints(int max) {
            config.setMaxConcurrentCheckpoints(max);
            return this;
        }

        public Builder maxRetainedCheckpoints(int max) {
            config.setMaxRetainedCheckpoints(max);
            return this;
        }

        public Builder storageType(String type) {
            config.setStorageType(type);
            return this;
        }

        public Builder storageConfig(Map<String, String> config) {
            this.config.setStorageConfig(config);
            return this;
        }

        public Builder storageProperty(String key, String value) {
            config.setStorageProperty(key, value);
            return this;
        }

        public CheckpointConfig build() {
            return config;
        }
    }
}
