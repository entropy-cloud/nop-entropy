/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.iterator;

import io.nop.api.core.exceptions.NopException;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import static io.nop.commons.CommonErrors.ERR_COLLECTIONS_ITERATOR_EOF;

public class FindNextPageIterator<T> implements Iterator<List<T>> {
    private final Function<T, List<T>> provider;

    private T lastEntity;
    private List<T> entityList;
    private boolean eof;

    public FindNextPageIterator(Function<T, List<T>> provider) {
        this.provider = provider;
    }

    @Override
    public boolean hasNext() {
        if (eof)
            return false;

        if (entityList != null && !entityList.isEmpty())
            return true;

        entityList = provider.apply(lastEntity);

        if (entityList.isEmpty()) {
            eof = true;
            return false;
        }
        lastEntity = entityList.get(entityList.size() - 1);
        return true;
    }

    @Override
    public List<T> next() {
        if (!hasNext())
            throw new NopException(ERR_COLLECTIONS_ITERATOR_EOF);
        List<T> list = entityList;
        this.entityList = null;
        return list;
    }
}