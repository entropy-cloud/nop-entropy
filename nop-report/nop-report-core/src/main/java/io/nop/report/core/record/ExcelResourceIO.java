package io.nop.report.core.record;

import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class ExcelResourceIO<T> implements IResourceRecordIO<T> {

    private ExcelIOConfig ioConfig;
    private List<String> headers;
    private List<String> headerLabels;
    private Type recordType = Map.class;

    public void setRecordType(Type recordType) {
        this.recordType = recordType;
    }

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
        ExcelRecordInput<T> input = new ExcelRecordInput<>(resource, getTypeInfo(resource), ioConfig);
        input.setHeaders(headers);
        input.setHeaderLabels(headerLabels);
        return input;
    }

    @Override
    public IRecordOutput<T> openOutput(IResource resource, String encoding) {
        ExcelRecordOutput<T> output = new ExcelRecordOutput<>(resource, ioConfig);
        output.setHeaders(headers);
        output.setHeaderLabels(headerLabels);
        return output;
    }

    protected IGenericType getTypeInfo(IResource resource) {
        if (recordType == null)
            return PredefinedGenericTypes.MAP_STRING_ANY_TYPE;
        return BeanTool.instance().getGenericType(recordType);
    }
}