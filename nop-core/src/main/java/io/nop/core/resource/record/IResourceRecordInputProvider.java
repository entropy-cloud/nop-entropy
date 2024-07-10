package io.nop.core.resource.record;

import io.nop.core.resource.IResource;
import io.nop.dataset.record.IRecordInput;

public interface IResourceRecordInputProvider<T> {
    IRecordInput<T> openInput(IResource resource, String encoding);
}
