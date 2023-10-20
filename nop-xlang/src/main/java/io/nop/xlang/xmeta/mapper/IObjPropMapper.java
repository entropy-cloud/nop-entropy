/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.mapper;

import io.nop.xlang.xmeta.IObjPropMeta;

/**
 * 对象属性在不同场景下进行序列化时可能对应不同的序列化要求
 */
public interface IObjPropMapper {
    Object mapTo(Object obj, IObjPropMeta propMeta, Object value);

    Object mapFrom(Object obj, IObjPropMeta propMeta, Object value);
}