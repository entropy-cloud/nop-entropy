package io.nop.record.resource;

import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;

public class ResourceRecordIO<T> implements IResourceRecordIO<T> {
    @Override
    public IRecordInput<T> openInput(IResource resource, String encoding) {
        return null;
    }

    @Override
    public IRecordOutput<T> openOutput(IResource resource, String encoding) {
        return null;
    }
}
