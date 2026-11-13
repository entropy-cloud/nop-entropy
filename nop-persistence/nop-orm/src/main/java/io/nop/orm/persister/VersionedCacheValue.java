/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

/**
 * plan 2517 R2-14 裁定⑥（C2-004）：全局缓存 value 版本化封装——由裸 Object[] 升为携带世代号的封装值。
 * 世代号语义唯一权威见 {@link GenerationFence}（设计文档 §3.4，C1-027 SSOT）。
 */
final class VersionedCacheValue {
    final long gen;
    final Object values;

    VersionedCacheValue(long gen, Object values) {
        this.gen = gen;
        this.values = values;
    }

    static Object wrap(long gen, Object values) {
        return new VersionedCacheValue(gen, values);
    }

    /**
     * 兼容解包：版本化封装值与历史裸 Object[]/List 形态均还原为裸值
     */
    static Object unwrap(Object value) {
        if (value instanceof VersionedCacheValue)
            return ((VersionedCacheValue) value).values;
        return value;
    }
}
