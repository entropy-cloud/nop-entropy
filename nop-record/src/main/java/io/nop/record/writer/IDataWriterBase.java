package io.nop.record.writer;

import java.io.Closeable;
import java.io.Flushable;

public interface IDataWriterBase extends Closeable, Flushable {
    long getWrittenCount();
}
