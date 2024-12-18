package io.nop.report.core.record;

import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;

import java.util.List;

public class ExcelResourceIO<T> implements IResourceRecordIO<T> {

    private ExcelIOConfig ioConfig;
    private List<String> headers;
    private List<String> headerLabels;

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public List<String> getHeaderLabels() {
        return headerLabels;
    }

    public void setHeaderLabels(List<String> headerLabels) {
        this.headerLabels = headerLabels;
    }

    public void setIOConfig(ExcelIOConfig outputConfig) {
        this.ioConfig = outputConfig;
    }


    @Override
    public IRecordInput<T> openInput(IResource resource, String encoding) {
        return null;
    }

    @Override
    public IRecordOutput<T> openOutput(IResource resource, String encoding) {
        ExcelRecordOutput<T> output = new ExcelRecordOutput<>(resource, ioConfig);
        output.setHeaders(headers);
        output.setHeaderLabels(headerLabels);
        return output;
    }
}