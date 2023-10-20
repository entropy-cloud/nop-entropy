/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tool.log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public class ChunkIterator<T> implements Iterator<List<T>> {
    private final Iterator<T> in;
    private final Predicate<T> startTest;

    private T next;

    public ChunkIterator(Iterator<T> in, Predicate<T> startTest) {
        this.in = in;
        this.startTest = startTest;
        while (in.hasNext()) {
            next = in.next();
            if (startTest.test(next)) {
                break;
            }
            next = null;
        }
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public List<T> next() {
        if (next == null)
            throw new IllegalStateException("eof");
        List<T> list = new ArrayList<>();
        list.add(next);

        next = null;
        while (in.hasNext()) {
            next = in.next();
            if (startTest.test(next)) {
                break;
            }
            list.add(next);
            next = null;
        }
        return list;
    }
}
