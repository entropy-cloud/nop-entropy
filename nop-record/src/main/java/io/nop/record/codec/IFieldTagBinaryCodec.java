package io.nop.record.codec;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.output.IRecordBinaryOutput;

public interface IFieldTagBinaryCodec {
    IBitSet decodeTags(IRecordBinaryInput input, RecordFieldMeta field, IFieldCodecContext context);

    IBitSet encodeTags(IRecordBinaryOutput output, Object value, RecordFieldMeta field, RecordObjectMeta typeMeta,
                       IFieldCodecContext context);
}