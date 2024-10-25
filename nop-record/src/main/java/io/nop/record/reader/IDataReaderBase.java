package io.nop.record.reader;

import java.io.Closeable;

public interface IDataReaderBase extends Closeable {
    boolean isEof();


}
