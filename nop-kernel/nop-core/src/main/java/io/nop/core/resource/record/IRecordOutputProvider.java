package io.nop.core.resource.record;

import io.nop.dataset.record.IRecordOutput;

public interface IRecordOutputProvider<T> {
    IRecordOutput<T> openOutput(String name);
}
