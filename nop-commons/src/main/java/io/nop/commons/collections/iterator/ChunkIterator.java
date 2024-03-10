/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.iterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ChunkIterator<T> implements Iterator<List<T>> {
    private final Iterator<T> it;
    private final int chunkSize;

    public ChunkIterator(Iterator<T> it, int chunkSize) {
        this.it = it;
        this.chunkSize = chunkSize;
    }

    @Override
    public boolean hasNext() {
        return it.hasNext();
    }

    @Override
    public List<T> next() {
        List<T> ret = new ArrayList<>(chunkSize);
        for (int i = 0; i < chunkSize; i++) {
            if (it.hasNext()) {
                ret.add(it.next());
            } else {
                break;
            }
        }
        return ret;
    }
}
