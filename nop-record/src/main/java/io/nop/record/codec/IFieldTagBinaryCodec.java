package io.nop.record.codec;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.reader.IRecordBinaryReader;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.writer.IRecordBinaryWriter;

public interface IFieldTagBinaryCodec {
    IBitSet decodeTags(IRecordBinaryReader input, RecordFieldMeta field, IFieldCodecContext context);

    IBitSet encodeTags(IRecordBinaryWriter output, Object value, RecordFieldMeta field, RecordObjectMeta typeMeta,
                       IFieldCodecContext context);
}