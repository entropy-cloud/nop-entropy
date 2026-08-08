/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

/**
 * Feishu scan-bind protocol, FeishuBindProvider (W5-2, not yet impl).
 *
 * <p>Implements IChannelBindProvider: builds the QR payload, handles the
 * Feishu scan callback, returns the open_id as the external id (extId).
 */
package io.nop.integration.feishu.bind;
