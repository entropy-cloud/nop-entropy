package io.nop.record.codec;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.writer.IRecordTextWriter;

public interface IFieldTagTextCodec {
    IBitSet decodeTags(IRecordTextWriter input, RecordFieldMeta field, IFieldCodecContext context);

    IBitSet encodeTags(IRecordTextWriter output, Object value, RecordFieldMeta field, RecordObjectMeta typeMeta,
                       IFieldCodecContext context);
}