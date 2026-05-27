/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.typeinfo;

import java.io.Serializable;

/**
 * Simple byte-level serializer for basic types.
 * Not to be confused with {@link io.nop.stream.core.common.typeutils.TypeSerializer}
 * which is the full-featured Flink-compatible serializer.
 */
public interface SimpleSerializer<T> extends Serializable {
    byte[] serialize(T value) throws Exception;

    T deserialize(byte[] data) throws Exception;
}
