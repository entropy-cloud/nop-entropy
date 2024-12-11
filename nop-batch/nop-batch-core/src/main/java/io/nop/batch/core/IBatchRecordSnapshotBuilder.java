package io.nop.batch.core;

import java.util.Collection;

public interface IBatchRecordSnapshotBuilder<S> {
    interface ISnapshot<S> {
        Collection<S> restore(Collection<S> items, IBatchChunkContext chunkContext);

        void onError(Throwable e);
    }

    ISnapshot<S> buildSnapshot(Collection<S> items, IBatchChunkContext chunkContext);
}
