package io.nop.record.serialization;

import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.writer.IRecordWriterBase;

import java.io.IOException;

public interface IModelBasedRecordSerializer<Output extends IRecordWriterBase> {
    boolean writeObject(Output out, RecordObjectMeta recordMeta, Object record, String name, IFieldCodecContext context) throws IOException;

    boolean writeField(Output out, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException;
}
