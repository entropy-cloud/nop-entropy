/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

/**
 * Feishu Stream connection lifecycle, FeishuClient (W5-1a, not yet impl).
 *
 * <p>Owns the long-lived Stream connection, reconnect, heartbeat, and
 * credential management (appId/appSecret via nop-config-encryption).
 */
package io.nop.integration.feishu.client;
