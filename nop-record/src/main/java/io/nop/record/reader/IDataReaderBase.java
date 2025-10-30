package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;

import java.io.Closeable;
import java.io.IOException;

public interface IDataReaderBase extends Closeable {
    boolean isEof() throws IOException;

    long pos() throws IOException;

    /**
     * 如果是subInput则返回底层的pos
     */
    default long realPos() {
        try {
            return pos();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    IDataReaderBase subInput(long maxLength) throws IOException;
}
