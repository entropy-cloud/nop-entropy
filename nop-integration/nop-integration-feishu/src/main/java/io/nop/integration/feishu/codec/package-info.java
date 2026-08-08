/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

/**
 * Feishu Pbbp2 protobuf binary codec, {@link io.nop.integration.feishu.codec.FeishuPbCodec}
 * (W5-1b).
 *
 * <p>Encodes/decodes method=0 CONTROL, method=1 DATA, method=2 ACK frames using
 * a hand-written protobuf wire format (no {@code protobuf-java} dependency).
 * Independently testable: pure byte in/out.
 */
package io.nop.integration.feishu.codec;
