/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * hash 结构上的 key-value 操作视图：以 hash field 作为 key 复用 {@link INosqlKeyValueOperations}。
 * <p>
 * 注意：继承的 putExAsync/getExAsync/setTimeoutAsync 等 TTL 相关方法，其 Expiry 作用于
 * <b>整个 hash key</b> 而不是单个 field——Redis 的 hash field 不支持独立 TTL。
 */
public interface INosqlHashOperations extends INosqlKeyValueOperations {

    CompletionStage<Map<String, Object>> getAllAsync();
}
