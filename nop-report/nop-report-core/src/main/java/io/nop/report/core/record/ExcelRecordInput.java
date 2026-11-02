package io.nop.report.core.record;

import io.nop.api.core.exceptions.NopException;
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
import io.nop.dataset.record.IRecordOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.XlsxConstants;
import io.nop.ooxml.xlsx.parse.XlsxToRecordOutput;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static io.nop.report.core.XptErrors.ARG_MAX_COUNT;
import static io.nop.report.core.XptErrors.ARG_SHEET_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_EXCEL_READ_ROW_COUNT_EXCEED_LIMIT;

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
        collector.setHeaders(headers);
        collector.setHeaderLabels(headerLabels);
        collector.setHeadersNormalizer(headersNormalizer);

        // 解析期按数据行计数并在超限时中止，保证 collector 物化的行数有上界
        this.output = new XlsxToRecordOutput(name -> new RowLimitedOutput(collector, name,
                config.getHeaderRowCount(), config.getMaxCountPerSheet()));
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

    /**
     * 数据行上界：表头行（前 headerRowCount 行）不计入，与写侧 maxCountPerSheet 的数据行口径一致；maxCount <= 0 不设上界
     */
    static final class RowLimitedOutput implements IRecordOutput<List<Object>> {
        private final IRecordOutput<List<Object>> target;
        private final String sheetName;
        private final int headerRowCount;
        private final int maxCount;
        private long rowCount;

        RowLimitedOutput(IRecordOutput<List<Object>> target, String sheetName, int headerRowCount, int maxCount) {
            this.target = target;
            this.sheetName = sheetName;
            this.headerRowCount = Math.max(headerRowCount, 0);
            this.maxCount = maxCount;
        }

        @Override
        public void beginWrite(Map<String, Object> headerMeta) throws IOException {
            target.beginWrite(headerMeta);
        }

        @Override
        public void endWrite(Map<String, Object> trailerMeta) throws IOException {
            target.endWrite(trailerMeta);
        }

        @Override
        public long getWriteCount() {
            return target.getWriteCount();
        }

        @Override
        public void write(List<Object> record) throws IOException {
            if (maxCount > 0 && rowCount >= headerRowCount + (long) maxCount)
                throw new NopException(ERR_XPT_EXCEL_READ_ROW_COUNT_EXCEED_LIMIT)
                        .param(ARG_SHEET_NAME, sheetName).param(ARG_MAX_COUNT, maxCount);
            rowCount++;
            target.write(record);
        }

        @Override
        public void flush() throws IOException {
            target.flush();
        }

        @Override
        public void close() throws IOException {
            target.close();
        }
    }
}
