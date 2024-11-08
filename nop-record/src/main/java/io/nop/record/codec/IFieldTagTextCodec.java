package io.nop.record.codec;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.reader.ITextDataReader;
import io.nop.record.writer.ITextDataWriter;

public interface IFieldTagTextCodec {
    IBitSet decodeTags(ITextDataReader input, RecordFieldMeta field, IFieldCodecContext context);

    IBitSet encodeTags(ITextDataWriter output, Object value, RecordFieldMeta field, RecordObjectMeta typeMeta,
                       IFieldCodecContext context);
}