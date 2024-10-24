package io.nop.record.reader;

import java.io.Closeable;

public interface IRecordReaderBase extends Closeable {
    boolean isEof();


}
