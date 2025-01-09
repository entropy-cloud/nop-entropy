package io.nop.report.core.record;

import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.record.list.HeaderListRecordOutput;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.dataset.record.IRecordInput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.XlsxConstants;
import io.nop.ooxml.xlsx.parse.XlsxToRecordOutput;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class ExcelRecordInput<T> implements IRecordInput<T> {
    private final IResource resource;
    private final ExcelIOConfig config;
    private final IGenericType beanType;

    private List<String> headers;
    private List<String> headerLabels;

    private ExcelWorkbook xptModel;

    private long readCount;

    private XlsxToRecordOutput output;

    private List<Map<String, Object>> list;

    private IEvalFunction headersNormalizer;
    private int nextIndex = 0;

    public ExcelRecordInput(IResource resource, IGenericType beanType, ExcelIOConfig config) {
        this.resource = resource;
        this.config = config == null ? ExcelIOConfig.DEFAULT : config;
        this.xptModel = loadXptModel(this.config);
        this.beanType = beanType;
    }

    public void setHeadersNormalizer(IEvalFunction headersNormalizer) {
        this.headersNormalizer = headersNormalizer;
    }

    private ExcelWorkbook loadXptModel(ExcelIOConfig config) {
        String tplPath = config.getTemplatePath();
        if (StringHelper.isEmpty(tplPath))
            tplPath = XlsxConstants.SIMPLE_DATA_TEMPLATE_PATH;
        return (ExcelWorkbook) ResourceComponentManager.instance().loadComponentModel(tplPath);
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

    @Override
    public long getReadCount() {
        return readCount;
    }

    @Override
    public void close() throws IOException {
        if (output != null)
            output.close();
    }

    @Override
    public void beforeRead(Map<String, Object> map) {
        HeaderListRecordOutput<Map<String, Object>> collector = new HeaderListRecordOutput<>(config.getHeaderRowCount(), CollectionHelper::toNonEmptyKeyMap);
        collector.setHeaderLabels(headerLabels);
        collector.setHeadersNormalizer(headersNormalizer);

        this.output = new XlsxToRecordOutput(name -> collector);
        this.output.loadFromResource(resource);
        String sheetName = config.getDataSheetName();
        this.output.parseSheet(sheetName);
        list = collector.getResult();
    }

    @Override
    public boolean hasNext() {
        return nextIndex < list.size();
    }

    @Override
    public T next() {
        if (hasNext()) {
            Map<String, Object> map = list.get(nextIndex);
            nextIndex++;
            readCount++;
            return buildResult(map);
        }
        throw new NoSuchElementException();
    }

    protected T buildResult(Map<String, Object> map) {
        if (beanType == null || beanType == PredefinedGenericTypes.MAP_STRING_ANY_TYPE)
            return (T) map;
        return (T) BeanTool.instance().buildBean(map, beanType, null);
    }
}
