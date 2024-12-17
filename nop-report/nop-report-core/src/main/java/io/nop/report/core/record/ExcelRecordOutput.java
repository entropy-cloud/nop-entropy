package io.nop.report.core.record;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.zip.ZipOptions;
import io.nop.dataset.record.IRecordOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.xlsx.output.GenState;
import io.nop.xlang.api.XLang;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ExcelRecordOutput<T> implements IRecordOutput<T> {
    private final IResource resource;
    private final ExcelIOConfig config;
    private ExcelWorkbook template;
    private long writeCount;
    private File tempDir;
    private final IEvalScope scope = XLang.newEvalScope();

    private List<String> headers;
    private boolean headersWritten;

    private boolean genTrailer;
    private GenState genState;

    public ExcelRecordOutput(IResource resource, List<String> headers,
                             ExcelIOConfig config) {
        this.resource = resource;
        try {
            this.config = config;
            this.headers = headers;
            this.tempDir = ResourceHelper.getTempResource("xlsx").toFile();
            this.tempDir.mkdirs();
        } catch (Exception e) {
            clearDir();
            throw NopException.adapt(e);
        }
    }

    private void clearDir() {
        if (tempDir != null)
            FileHelper.deleteAll(tempDir);
    }

    @Override
    public void flush() {

    }

    @Override
    public long getWriteCount() {
        return writeCount;
    }

    @Override
    public void write(T record) {
        writeCount++;

        if (!headersWritten) {
            headersWritten = true;
            writeHeaders();
        }
    }

    private void writeHeaders() {

    }

    @Override
    public void close() throws IOException {
        if (tempDir != null && genTrailer) {
            genState.pkg.generateToDir(tempDir, scope);

            ZipOptions options = new ZipOptions();
            String password = (String) scope.getValue(OfficeConstants.VAR_FILE_PASSWORD);
            options.setPassword(password);
            ResourceHelper.zipDir(new FileResource(tempDir), resource, options);
        }

        this.clearDir();
    }

    @Override
    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    @Override
    public void beginWrite(Map<String, Object> attributes) {
        if (attributes != null)
            this.scope.setLocalValues(attributes);
    }

    @Override
    public void endWrite(Map<String, Object> trailerMeta) {
        this.genTrailer = true;
    }
}