package io.nop.http.client.jdk;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * nop-http-client-jdk 叶内配置（plan 2505 / 4.3-C3-002 + CL-026）。
 * <p>
 * 按 plan 2505 PA 裁定（设计权威来源
 * {@code ai-dev/design/concurrency/sse-backpressure-and-body-phase-lifecycle.md} §3.4），
 * body 阶段 idle 读超时的预算承载为本叶内配置键——不触碰 nop-http-api 配置面。
 */
public interface JdkHttpConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(JdkHttpConfigs.class);

    @Description("Body-phase idle read timeout in milliseconds for download/SSE response streams "
            + "(JDK client). When no byte arrives within this budget (or the cancel token fires), "
            + "the watchdog closes the underlying stream so the blocked read exits. "
            + "Independent of the request-timeout budget that only covers response headers. "
            + "Set to 0 or a negative value to disable the watchdog (not recommended)")
    IConfigReference<Integer> CFG_HTTP_BODY_IDLE_TIMEOUT =
            varRef(s_loc, "nop.http.client.jdk.body-idle-timeout", Integer.class, 120_000);
}
