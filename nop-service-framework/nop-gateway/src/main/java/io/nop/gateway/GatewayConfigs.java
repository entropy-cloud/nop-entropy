/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface GatewayConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(GatewayConfigs.class);

    @Description("网关模型文件对应的虚拟文件路径")
    IConfigReference<String> CFG_GATEWAY_MODEL_PATH =
            varRef(s_loc, "nop.gateway.model-path", String.class, "/nop/main/app.gateway.xml");

    /**
     * 2.3-C8-002（plan 2508 裁定②）：url 路由腿上游在途并发上界（主腿含重试挂起窗 + fallback
     * 腿共享许可池）。≤0 = 不设上界档（退化为现行无界语义）。上界使「在途内存持有 × 挂起时长」
     * 与请求到达率解耦，消除 429 重试放大 × 挂起窗的确定性 OOM 轨迹。
     */
    @Description("上游 HTTP 在途并发上界（主腿+fallback 共享；≤0=不设上界）")
    IConfigReference<Integer> CFG_GATEWAY_UPSTREAM_MAX_INFLIGHT =
            varRef(s_loc, "nop.gateway.upstream-max-inflight", Integer.class, 1000);

    /**
     * 2.3-C8-002（plan 2508 裁定②(c)）：主链 429/5xx 重试次数上限。默认 3 = 现行硬编码行为；
     * ≤0 = 零重试（429/5xx 首次响应即终态失败）。
     */
    @Description("上游主链重试次数上限（≤0=零重试；默认 3=现行行为）")
    IConfigReference<Integer> CFG_GATEWAY_UPSTREAM_MAX_RETRIES =
            varRef(s_loc, "nop.gateway.upstream-max-retries", Integer.class, 3);

    /**
     * 2.3-C3-004（plan 2550 裁定 C）：流式首段窗口内失败的重订阅次数上界
     * （BufferedStreamingPublisher 重订阅链）。≤0 = 不设上界（保持现行无限重订阅语义）。
     * 上界配合指数退避消除「上游恒失败 × 窗口内重订阅无界」的连接/重试放大轨迹。
     */
    @Description("流式首段重订阅次数上界（≤0=无限；默认 5）")
    IConfigReference<Integer> CFG_GATEWAY_STREAM_MAX_RESUBSCRIBE_ATTEMPTS =
            varRef(s_loc, "nop.graphql.stream.max-resubscribe-attempts", Integer.class, 5);

    /**
     * 2.3-C4-003（plan 2551 裁定 C②）：上游 HTTP 请求显式超时（缺省 30s；≤0=用客户端缺省）。
     * 429/5xx 重试挂起窗与请求超时构成双重上界，无响应的上游不再无限占准入许可与内存。
     */
    @Description("上游 HTTP 请求超时毫秒（≤0=客户端缺省；默认 30000）")
    IConfigReference<Long> CFG_GATEWAY_UPSTREAM_REQUEST_TIMEOUT =
            varRef(s_loc, "nop.gateway.upstream-request-timeout", Long.class, 30_000L);

    /**
     * 2.3-C8-003（plan 2551 裁定 G）：流式聚合缓冲字节上界（缺省 2MB）——content 与每个
     * tool-call arguments 缓冲分别受界，超限截断 + 一次性 WARN + 截断标记。
     */
    @Description("流式聚合缓冲字节上界（content 与每个 tool-call arguments 分别受界；默认 2MB）")
    IConfigReference<Long> CFG_GATEWAY_STREAM_ACCUMULATE_MAX_BYTES =
            varRef(s_loc, "nop.gateway.streaming.accumulate-max-bytes", Long.class, 2_097_152L);
}
