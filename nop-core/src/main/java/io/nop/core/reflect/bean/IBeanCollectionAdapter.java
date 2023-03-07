/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import java.util.Iterator;
import java.util.function.ObjIntConsumer;

public interface IBeanCollectionAdapter {
    int getSize(Object bean);

    Class<?> getComponentType(Object bean);

    void forEach(Object bean, ObjIntConsumer<Object> action);

    Iterator<Object> iterator(Object bean);
}