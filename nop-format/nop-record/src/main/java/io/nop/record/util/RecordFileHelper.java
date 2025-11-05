package io.nop.record.util;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.dataset.record.IRecordOutput;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.resource.ModelBasedResourceRecordIO;

import java.io.IOException;
import java.util.List;

public class RecordFileHelper {
    public static void writeRecords(IResource resource, RecordFileMeta fileMeta, List<?> records) {
        ModelBasedResourceRecordIO<Object> io = ModelBasedResourceRecordIO.fromFileModel(fileMeta);
        IRecordOutput<Object> output = io.openOutput(resource, null);
        try {
            output.writeBatch(records);
            output.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(output);
        }
    }
}
