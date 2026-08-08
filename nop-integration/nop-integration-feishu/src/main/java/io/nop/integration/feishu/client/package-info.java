/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

/**
 * Feishu Stream connection lifecycle + Open API client,
 * {@link io.nop.integration.feishu.client.FeishuClient} (W5-1a / W5-1c).
 *
 * <p>Owns the long-lived Stream connection (reconnect + heartbeat), the Pbbp2
 * frame dispatch into {@link io.nop.integration.feishu.client.IMessageHandler},
 * the {@code tenant_access_token} cache, and credential management
 * (appId/appSecret/verificationToken/encryptKey via {@code @InjectValue} +
 * Nop {@code @sec:} config encryption).
 */
package io.nop.integration.feishu.client;
