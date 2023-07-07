package io.nop.record.output;

import java.nio.ByteBuffer;

public interface IRecordBinaryOutput {
    IRecordBinaryOutput append(byte[] bytes);

    IRecordBinaryOutput append(byte[] str, int start, int end);

    IRecordBinaryOutput append(byte c);

    IRecordBinaryOutput append(ByteBuffer buf);
}
