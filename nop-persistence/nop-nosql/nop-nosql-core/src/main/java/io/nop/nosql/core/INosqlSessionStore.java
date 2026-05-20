/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface INosqlSessionStore {
    CompletableFuture<Map<String, Object>> getAsync(String sessionId);

    Map<String, Object> get(String sessionId);

    CompletableFuture<Object> getFieldAsync(String sessionId, String field);

    Object getField(String sessionId, String field);

    CompletableFuture<Void> setAsync(String sessionId, Map<String, Object> data, long ttlMs);

    /**
     * Note: This method is non-atomic. It uses HMSET followed by PEXPIRE.
     * If PEXPIRE fails, the data will persist without TTL.
     */
    void set(String sessionId, Map<String, Object> data, long ttlMs);

    CompletableFuture<Void> setFieldAsync(String sessionId, String field, Object value);

    void setField(String sessionId, String field, Object value);

    CompletableFuture<Boolean> touchAsync(String sessionId, long ttlMs);

    boolean touch(String sessionId, long ttlMs);

    CompletableFuture<Void> removeAsync(String sessionId);

    void remove(String sessionId);

    CompletableFuture<Boolean> existsAsync(String sessionId);

    boolean exists(String sessionId);
}
