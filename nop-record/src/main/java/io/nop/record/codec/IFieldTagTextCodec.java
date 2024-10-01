package io.nop.record.codec;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordTypeMeta;
import io.nop.record.output.IRecordTextOutput;

public interface IFieldTagTextCodec {
    IBitSet decodeTags(IRecordTextOutput input, RecordFieldMeta field, IFieldCodecContext context);

    IBitSet encodeTags(IRecordTextOutput output, Object value, RecordFieldMeta field, RecordObjectMeta typeMeta,
                       IFieldCodecContext context);
}