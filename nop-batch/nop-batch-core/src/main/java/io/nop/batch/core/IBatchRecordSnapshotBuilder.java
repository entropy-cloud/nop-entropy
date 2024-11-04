package io.nop.batch.core;

import java.util.List;

public interface IBatchRecordSnapshotBuilder<S> {
    interface ISnapshot<S> {
        List<S> restore(List<S> items, IBatchChunkContext chunkContext);
    }

    ISnapshot<S> buildSnapshot(List<S> items, IBatchChunkContext chunkContext);
}
