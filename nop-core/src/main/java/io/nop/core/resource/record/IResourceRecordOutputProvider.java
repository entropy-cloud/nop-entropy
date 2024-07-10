package io.nop.core.resource.record;

import io.nop.core.resource.IResource;
import io.nop.dataset.record.IRecordOutput;

public interface IResourceRecordOutputProvider<T> {
    IRecordOutput<T> openOutput(IResource resource, String encoding);
}
