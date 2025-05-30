package io.nop.record.codec;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.io.IOException;

public interface IFieldTagBinaryCodec {
    IBitSet decodeTags(IBinaryDataReader input, RecordObjectMeta typeMeta,
                       IFieldCodecContext context) throws IOException;

    IBitSet encodeTags(IBinaryDataWriter output, Object value, RecordObjectMeta typeMeta,
                       IFieldCodecContext context) throws IOException;
}