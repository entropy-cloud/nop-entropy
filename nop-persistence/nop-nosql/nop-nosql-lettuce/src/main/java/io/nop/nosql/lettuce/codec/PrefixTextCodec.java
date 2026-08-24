/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.codec;

import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.nop.commons.util.ClassHelper;
import io.nop.core.lang.json.PrefixEncodeHelper;

import java.nio.ByteBuffer;

/**
 * 以文本形式存储 key/value 的 codec：key 为 UTF-8 文本，value 经 PrefixEncodeHelper 前缀编码。
 * <p>
 * 安全说明（信任边界）：解码 {@code $d:className} 前缀的数据时，会按存储文本中携带的类名加载类
 * 并做 JSON 绑定，<b>没有类白名单过滤</b>。因此使用本 codec 的 Redis 实例必须处于受信环境
 * （专用实例、网络隔离、通过 ACL 限制写入方）——能写入 Redis 的主体等同于能影响本进程的
 * 对象反序列化面。
 */
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
