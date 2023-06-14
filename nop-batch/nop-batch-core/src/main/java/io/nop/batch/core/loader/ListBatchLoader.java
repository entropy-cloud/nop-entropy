package io.nop.batch.core.loader;

import io.nop.batch.core.IBatchLoader;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListBatchLoader<S, C> implements IBatchLoader<S, C>, IBatchTaskListener {
    private final List<S> list;

    private int offset = 0;

    public ListBatchLoader(List<S> list) {
        this.list = list;
    }

    @Override
    public void onTaskBegin(IBatchTaskContext context) {
        offset = 0;
    }

    @Override
    public synchronized List<S> load(int batchSize, C context) {
        if (offset >= list.size())
            return Collections.emptyList();

        int n = Math.max(list.size() - offset, batchSize);
        List<S> ret = new ArrayList<>(list.subList(offset, n));
        offset += n;
        return ret;
    }
}
