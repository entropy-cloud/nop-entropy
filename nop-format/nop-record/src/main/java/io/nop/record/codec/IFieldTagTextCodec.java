package io.nop.record.codec;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.reader.ITextDataReader;
import io.nop.record.writer.ITextDataWriter;

public interface IFieldTagTextCodec {
    IBitSet decodeTags(ITextDataReader input, RecordObjectMeta typeMeta, IFieldCodecContext context);

    IBitSet encodeTags(ITextDataWriter output, Object value, RecordObjectMeta typeMeta,
                       IFieldCodecContext context);
}