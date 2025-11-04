/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.serialize;

public interface IObjectSerializer {
    Object serializeTo(Object o);

    Object deserializeFrom(Object s);

    IObjectSerializer IDENTITY = new IObjectSerializer() {
        @Override
        public Object serializeTo(Object o) {
            return o;
        }

        @Override
        public Object deserializeFrom(Object s) {
            return s;
        }
    };
}