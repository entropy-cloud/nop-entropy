/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.mock;

import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * Mock AI服务的配置项。
 */
public interface MockChatConfigs {
    SourceLocation S_LOC = SourceLocation.fromClass(MockChatConfigs.class);

    /**
     * Mock响应文件存储目录
     */
    IConfigReference<String> CFG_AI_MOCK_DIR = varRef(S_LOC, "nop.ai.mock.dir", String.class, "./ai-mock");

    /**
     * 响应结束标记
     */
    IConfigReference<String> CFG_AI_MOCK_EOF_MARKER = varRef(S_LOC, "nop.ai.mock.eof-marker", String.class, "\nNOP_EOF");

    /**
     * 轮询间隔（毫秒）
     */
    IConfigReference<Long> CFG_AI_MOCK_POLL_INTERVAL_MS = varRef(S_LOC, "nop.ai.mock.poll-interval-ms", Long.class, 500L);

    /**
     * 最大等待时间（小时）
     */
    IConfigReference<Long> CFG_AI_MOCK_TIMEOUT_HOURS = varRef(S_LOC, "nop.ai.mock.timeout-hours", Long.class, 2L);

    /**
     * 是否启用流式响应模拟
     */
    IConfigReference<Boolean> CFG_AI_MOCK_ENABLE_STREAM = varRef(S_LOC, "nop.ai.mock.enable-stream", Boolean.class, false);

    /**
     * 流式响应字符延迟（毫秒）
     */
    IConfigReference<Long> CFG_AI_MOCK_STREAM_DELAY_MS = varRef(S_LOC, "nop.ai.mock.stream-delay-ms", Long.class, 50L);
}
