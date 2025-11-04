package io.nop.core.resource.record.list;

import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.impl.BaseRecordInput;

import java.util.Collections;
import java.util.List;

public class ListRecordIO<T> implements IResourceRecordIO<T> {
    private List<T> records = Collections.emptyList();

    public void setRecords(List<T> records) {
        this.records = records;
    }

    @Override
    public IRecordInput<T> openInput(IResource resource, String encoding) {
        return new BaseRecordInput<>(records, null);
    }

    @Override
    public ListRecordOutput<T> openOutput(IResource resource, String encoding) {
        return new ListRecordOutput<>(resource, encoding);
    }
}
