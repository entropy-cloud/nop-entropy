/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.iterator;

import java.lang.reflect.Array;
import java.util.Iterator;

public class ArrayIterator implements Iterator<Object> {
    private final Object array;
    private int index;
    private int len;

    public ArrayIterator(Object array) {
        this.array = array;
        this.len = Array.getLength(array);
    }

    @Override
    public boolean hasNext() {
        return index < len;
    }

    @Override
    public Object next() {
        return Array.get(array, index++);
    }
}