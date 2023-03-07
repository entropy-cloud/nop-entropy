/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.nosql.lettuce.codec;

import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.nop.commons.util.ClassHelper;
import io.nop.core.lang.json.PrefixEncodeHelper;

import java.nio.ByteBuffer;

public class PrefixTextCodec implements RedisCodec<String, Object> {
    @Override
    public String decodeKey(ByteBuffer bytes) {
        return StringCodec.UTF8.decodeKey(bytes);
    }

    @Override
    public Object decodeValue(ByteBuffer bytes) {
        String str = StringCodec.UTF8.decodeValue(bytes);
        return PrefixEncodeHelper.decode(str, ClassHelper.getSafeClassLoader());
    }

    @Override
    public ByteBuffer encodeKey(String key) {
        return StringCodec.UTF8.encodeKey(key);
    }

    @Override
    public ByteBuffer encodeValue(Object value) {
        String str = PrefixEncodeHelper.encode(value);
        return StringCodec.UTF8.encodeValue(str);
    }
}
