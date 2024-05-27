package io.nop.batch.core;

import java.util.List;

public interface IBatchRecordSnapshotBuilder<T> {
    interface ISnapshot<T> {
        List<T> restore(List<T> items);
    }

    ISnapshot<T> buildSnapshot(List<T> items);
}
