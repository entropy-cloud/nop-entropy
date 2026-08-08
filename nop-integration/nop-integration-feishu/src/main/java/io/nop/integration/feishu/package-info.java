/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

/**
 * Feishu channel integration, vendor SDK protocol layer.
 *
 * <p>This module depends only on {@code nop-integration-api}, never on
 * {@code nop-ai-*} or {@code nop-auth-*}, so the Feishu protocol stays
 * reusable by non-AI scenarios. Sub-packages:
 * <ul>
 *   <li>{@link io.nop.integration.feishu.client} - FeishuClient (W5-1a)</li>
 *   <li>{@link io.nop.integration.feishu.codec} - FeishuPbCodec (W5-1b)</li>
 *   <li>{@link io.nop.integration.feishu.bind} - FeishuBindProvider (W5-2)</li>
 * </ul>
 *
 * <p>This plan (W0) creates the skeleton only; impl classes land later.
 */
package io.nop.integration.feishu;
