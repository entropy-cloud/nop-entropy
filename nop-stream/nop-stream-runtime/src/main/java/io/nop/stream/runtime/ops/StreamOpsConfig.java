/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;

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
    /**
     * F-09b (plan 2026-09-04-1326-3): minimal bearer-token auth for the ops endpoint.
     * REQUIRED (fail-fast at server start) whenever the bind address is not loopback —
     * a cross-machine ops endpoint (0.0.0.0 etc.) without a token is an unauthenticated
     * job-lifecycle/threaddump/metrics surface and the server refuses to start. Loopback
     * binding keeps its zero-auth default (back-compat); an explicitly configured token
     * is enforced on any bind address.
     */
    public static final String KEY_AUTH_TOKEN = "nop.stream.ops.http.token";

    /**
     * W-01 (CRA 8.42 R4-02, ruling A.1): upper bound for the POST /jobs request
     * body. JobSubmissionSpec is three fields (jobId / pipelineFactoryClass /
     * params map) — the 1 MiB default covers legal payloads with ~3 orders of
     * magnitude of headroom; the key exists so an unusually large params payload
     * can be accommodated without code change (mis-kill mitigation). Values
     * &le; 0 fail fast typed at assembly (parameter discipline aligned with
     * adjudication ②).
     */
    public static final String KEY_MAX_BODY_BYTES = "nop.stream.ops.http.max-body-bytes";

    public static final int DEFAULT_PORT = 8901;
    public static final String DEFAULT_BIND = "127.0.0.1";
    public static final int DEFAULT_MAX_BODY_BYTES = 1048576;

    private boolean enabled = false;
    private int port = DEFAULT_PORT;
    private String bindAddress = DEFAULT_BIND;
    private boolean metricsEnabled = true;
    private String authToken;
    private int maxBodyBytes = DEFAULT_MAX_BODY_BYTES;

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
                throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Invalid " + KEY_PORT + " value: " + port + " (expected integer)");
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
        String token = props.apply(KEY_AUTH_TOKEN);
        if (token != null && !token.isBlank()) {
            config.setAuthToken(token.trim());
        }
        String maxBody = props.apply(KEY_MAX_BODY_BYTES);
        if (maxBody != null && !maxBody.isBlank()) {
            try {
                config.setMaxBodyBytes(Integer.parseInt(maxBody.trim()));
            } catch (NumberFormatException e) {
                throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Invalid " + KEY_MAX_BODY_BYTES + " value: " + maxBody + " (expected integer)");
            }
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
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Invalid ops http port: " + port);
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

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    /** True when the bind address is loopback (127.0.0.1 / localhost / ::1). */
    public boolean isLoopbackBind() {
        if (bindAddress == null) {
            return true;
        }
        return "127.0.0.1".equals(bindAddress) || "localhost".equals(bindAddress)
                || "::1".equals(bindAddress) || "0:0:0:0:0:0:0:1".equals(bindAddress);
    }

    /** True when requests must present the bearer token (token configured). */
    public boolean isAuthRequired() {
        return authToken != null && !authToken.isBlank();
    }

    /** Upper bound for the POST /jobs request body in bytes (ruling A.1). */
    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    /**
     * Sets the submit-body bound. Values &le; 0 fail fast typed (parameter
     * discipline — an unbounded or zero-limit gate is a configuration error,
     * never a silent pass-through).
     */
    public void setMaxBodyBytes(int maxBodyBytes) {
        if (maxBodyBytes <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                    "Invalid " + KEY_MAX_BODY_BYTES + " value: " + maxBodyBytes + " (must be > 0)");
        }
        this.maxBodyBytes = maxBodyBytes;
    }
}
