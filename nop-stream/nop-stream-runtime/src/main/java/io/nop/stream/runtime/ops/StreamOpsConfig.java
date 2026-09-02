/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

/**
 * Item 16 (P-REQ-3/5): configuration of the ops HTTP server hosted in the
 * coordinator process. Default is DISABLED — no HTTP listener exists unless
 * explicitly enabled (explicit off semantics; the launch path fails fast on
 * an invalid config instead of silently skipping).
 */
public class StreamOpsConfig {

    public static final String KEY_ENABLED = "nop.stream.ops.http.enabled";
    public static final String KEY_PORT = "nop.stream.ops.http.port";
    public static final String KEY_BIND = "nop.stream.ops.http.bind";
    public static final String KEY_METRICS_ENABLED = "nop.stream.ops.metrics.enabled";

    public static final int DEFAULT_PORT = 8901;
    public static final String DEFAULT_BIND = "127.0.0.1";

    private boolean enabled = false;
    private int port = DEFAULT_PORT;
    private String bindAddress = DEFAULT_BIND;
    private boolean metricsEnabled = true;

    public static StreamOpsConfig fromProperties(java.util.function.Function<String, String> props) {
        StreamOpsConfig config = new StreamOpsConfig();
        String enabled = props.apply(KEY_ENABLED);
        if (enabled != null) {
            config.setEnabled(Boolean.parseBoolean(enabled.trim()));
        }
        String port = props.apply(KEY_PORT);
        if (port != null && !port.isBlank()) {
            try {
                config.setPort(Integer.parseInt(port.trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid " + KEY_PORT + " value: " + port + " (expected integer)");
            }
        }
        String bind = props.apply(KEY_BIND);
        if (bind != null && !bind.isBlank()) {
            config.setBindAddress(bind.trim());
        }
        String metrics = props.apply(KEY_METRICS_ENABLED);
        if (metrics != null) {
            config.setMetricsEnabled(Boolean.parseBoolean(metrics.trim()));
        }
        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid ops http port: " + port);
        }
        this.port = port;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }
}
