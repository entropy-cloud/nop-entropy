/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import io.nop.commons.collections.IAsyncMap;

import java.util.concurrent.CompletionStage;

/**
 * string key-value 操作接口。
 * <p>
 * 值类型契约：实现方通常将值序列化为文本存储（例如 nosql-lettuce 的 PrefixTextCodec）。
 * Number/Boolean 等基础类型经 put/get 往返后会退化为 String（编码为裸文本，解码不自动转型），
 * 调用方必须自行做类型规整，或者统一以 String 类型存取。
 */
public interface INosqlKeyValueOperations extends IAsyncMap<String, Object> {
    CompletionStage<Long> getSizeAsync();

    long getSize();

    /**
     * 写入值并在 timeout 毫秒后过期。
     */
    CompletionStage<Void> putExAsync(String key, Object value, long timeout);

    /**
     * 读取值，并将 key 的剩余存活时间刷新为 timeout 毫秒。
     */
    CompletionStage<Object> getExAsync(String key, long timeout);

    CompletionStage<Boolean> putIfAbsentExAsync(String key, Object value, long timeout);

    CompletionStage<String> putIfAbsentOrMatchExAsync(String key, String value, long timeout);

    CompletionStage<Object> getAndSetExAsync(String key, Object value, long timeout);

    CompletionStage<Long> getTimeoutAsync(String key);

    /**
     * 设置 key 的剩余存活时间为 timeout 毫秒。
     */
    CompletionStage<Boolean> setTimeoutAsync(String key, long timeout);
}
