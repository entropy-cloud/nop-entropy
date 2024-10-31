package io.nop.record.serialization;

import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.reader.IDataReaderBase;

import java.io.IOException;

public interface IModelBasedRecordDeserializer<Input extends IDataReaderBase> {
    Object readObject(Input in, RecordObjectMeta recordMeta, String name,
                      IFieldCodecContext context) throws IOException;

    boolean readField(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException;
}
