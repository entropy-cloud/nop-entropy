package io.nop.report.core.record;

import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;

public class ExcelResourceIO<T> implements IResourceRecordIO<T> {

    private ExcelIOConfig ioConfig;

    public void setIOConfig(ExcelIOConfig outputConfig) {
        this.ioConfig = outputConfig;
    }

    @Override
    public IRecordInput<T> openInput(IResource resource, String encoding) {
        return null;
    }

    @Override
    public IRecordOutput<T> openOutput(IResource resource, String encoding) {
        return new ExcelRecordOutput<>(resource, null, ioConfig);
    }
}