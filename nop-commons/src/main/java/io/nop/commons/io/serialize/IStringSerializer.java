/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.io.serialize;

public interface IStringSerializer {
    String serializeToString(Object o);

    Object deserializeFromString(String s);

    /**
     * 反序列化字符串到指定对象类型
     *
     * @param s
     * @param targetClass
     * @return
     */
    Object deserializeFromString(String s, Class<?> targetClass);
}