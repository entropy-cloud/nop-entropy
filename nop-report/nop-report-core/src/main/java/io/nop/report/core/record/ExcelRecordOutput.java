package io.nop.report.core.record;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ICellView;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.zip.ZipOptions;
import io.nop.dataset.record.IRecordOutput;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelCell;
import io.nop.excel.model.IExcelSheet;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.xlsx.XlsxConstants;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import io.nop.ooxml.xlsx.model.StylesPart;
import io.nop.ooxml.xlsx.output.ExcelSheetWriteSupport;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.ooxml.xlsx.output.GenState;
import io.nop.report.core.engine.ExpandedSheetGenerator;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.xlang.api.XLang;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ExcelRecordOutput<T> implements IRecordOutput<T> {
    private final IResource resource;
    private final ExcelIOConfig config;

    private long writeCount;
    private File tempDir;
    private final IXptRuntime xptRt;
    private final IEvalScope scope;

    private boolean genTrailer;
    private GenState genState;
    private ExcelWorkbook xptModel;

    private ExcelSheetWriteSupport out;
    private ExcelSheet dataSheetModel;

    private boolean headersWritten;
    private List<String> headers;
    private List<String> headerLabels;

    private ExcelRow row;

    public ExcelRecordOutput(IResource resource,
                             ExcelIOConfig config) {
        this.resource = resource;
        try {
            this.config = config == null ? ExcelIOConfig.DEFAULT : config;
            this.xptModel = loadXptModel(this.config);
            this.tempDir = ResourceHelper.getTempResource("xlsx").toFile();
            this.tempDir.mkdirs();
            this.xptRt = ExpandedSheetGenerator.newXptRuntime(XLang.newEvalScope(), xptModel);
            this.scope = xptRt.getEvalScope();
            this.genState = new GenState(ExcelOfficePackage.loadEmpty());
        } catch (Exception e) {
            clearDir();
            throw NopException.adapt(e);
        }
    }

    public List<String> getHeaderLabels() {
        return headerLabels;
    }

    public void setHeaderLabels(List<String> headerLabels) {
        this.headerLabels = headerLabels;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    private ExcelWorkbook loadXptModel(ExcelIOConfig config) {
        String tplPath = config.getTemplatePath();
        if (StringHelper.isEmpty(tplPath))
            tplPath = XlsxConstants.SIMPLE_DATA_TEMPLATE_PATH;
        return (ExcelWorkbook) ResourceComponentManager.instance().loadComponentModel(tplPath);
    }

    private void clearDir() {
        if (tempDir != null)
            FileHelper.deleteAll(tempDir);
    }

    @Override
    public void flush() throws IOException {
        if (out != null)
            out.flush();
    }

    @Override
    public long getWriteCount() {
        return writeCount;
    }

    @Override
    public void write(T record) {
        writeCount++;
        writeHeaders();

        ExcelRow row = makeRow(record);
        out.writeRow((int) writeCount, row);
    }

    private void writeHeaders() {
        if (!headersWritten) {
            headersWritten = true;
            ExcelRow row = new ExcelRow();
            String styleId = getCellStyleId(dataSheetModel.getTable().getCell(0, 0));
            for (String header : headers) {
                ExcelCell cell = new ExcelCell();
                cell.setStyleId(styleId);
                cell.setValue(header);
                row.internalAddCell(cell);
            }
            out.writeRow(0, row);
        }
    }

    ExcelRow makeRow(T record) {
        if (row == null) {
            row = new ExcelRow();
            String styleId = getCellStyleId(dataSheetModel.getTable().getCell(1, 0));
            for (String header : headers) {
                Object value = BeanTool.getComplexProperty(record, header);
                ExcelCell cell = new ExcelCell();
                cell.setStyleId(styleId);
                cell.setValue(value);
                row.internalAddCell(cell);
            }
        } else {
            for (int i = 0, n = headers.size(); i < n; i++) {
                String header = headers.get(i);
                Object value = BeanTool.getComplexProperty(record, header);
                row.getCells().get(i).setValue(value);
            }
        }
        return row;
    }

    String getCellStyleId(ICellView cell){
        return cell == null ? null : cell.getStyleId();
    }

    @Override
    public void beginWrite(Map<String, Object> attributes) {
        if (attributes != null)
            this.scope.setLocalValues(attributes);

        if (xptModel.getModel() != null && xptModel.getModel().getBeforeExpand() != null)
            xptModel.getModel().getBeforeExpand().invoke(xptRt);

        genSheet(config.getHeaderSheetName());

        newDataSheetWriter();
    }

    void newDataSheetWriter() {
        int index = genState.genSheetIndex();
        ExcelOfficePackage pkg = genState.pkg;
        String sheetName = config.getDataSheetName();
        if (StringHelper.isEmpty(sheetName))
            sheetName = "Data";
        String sheetPath = pkg.addSheet(index, sheetName, false);

        dataSheetModel = xptModel.requireSheet(sheetName);

        IResource file = new FileResource(new File(tempDir, sheetPath));
        out = new ExcelSheetWriteSupport(xptModel, index, file);

        CellRange cellRange = dataSheetModel.getTable().getCellRange();
        out.beginSheet(cellRange, dataSheetModel.getDefaultRowHeight(), dataSheetModel.getDefaultColumnWidth(),
                dataSheetModel.getTable().getCols());

        out.beginRows();
    }

    void closeDataSheetWriter() throws IOException {
        if (out != null) {
            out.endRows();
            out.endSheet(dataSheetModel.getPageMargins(), dataSheetModel.getPageSetup(), null);
            out.flush();
            out.close();
            out = null;
        }
    }

    @Override
    public void endWrite(Map<String, Object> trailerMeta) throws IOException {
        flush();
        genSheet(config.getTrailerSheetName());
        this.genTrailer = true;
    }

    void genSheet(String sheetName) {
        if (StringHelper.isEmpty(sheetName))
            return;

        ExcelSheet sheet = xptModel.requireSheet(sheetName);
        IExcelSheet expandedSheet = new ExpandedSheetGenerator(xptModel).generateSheet(sheet, xptRt, null);
        new ExcelTemplate(genState.pkg, xptModel, null).generateSheet(tempDir, expandedSheet, xptRt, genState);
    }

    @Override
    public void close() throws IOException {
        if (tempDir != null && genTrailer) {
            closeDataSheetWriter();

            genState.pkg.addFile(new StylesPart(xptModel.getStyles()));

            genState.pkg.generateToDir(tempDir, scope);

            if (xptModel.getModel() != null && xptModel.getModel().getAfterExpand() != null)
                xptModel.getModel().getAfterExpand().invoke(xptRt);

            ZipOptions options = new ZipOptions();
            String password = (String) scope.getValue(OfficeConstants.VAR_FILE_PASSWORD);
            options.setPassword(password);
            ResourceHelper.zipDir(new FileResource(tempDir), resource, options);
        }

        this.clearDir();
    }
}