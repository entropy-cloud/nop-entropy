package io.nop.record.codec;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;

public interface IFieldTagBinaryCodec {
    IBitSet decodeTags(IBinaryDataReader input, RecordFieldMeta field,
                       IFieldCodecContext context) throws IOException;

    IBitSet encodeTags(IBinaryDataWriter output, Object value, RecordFieldMeta field, RecordObjectMeta typeMeta,
                       IFieldCodecContext context) throws IOException;
}