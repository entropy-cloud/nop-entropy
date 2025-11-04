/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.delta;

import io.nop.xlang.xmeta.IObjMeta;

/**
 * 根据objMeta指定的对象结构和合并策略，将对象树合并到目标对象树上。
 */
public interface IObjMerger {
    void merge(Object target, Object partial, IObjMeta objMeta);
}
