package io.nop.tool.log;

import io.nop.api.core.exceptions.NopException;

import java.io.BufferedReader;
import java.util.Iterator;

public class LineIterator implements Iterator<String> {
    private final BufferedReader in;

    private String line;

    public LineIterator(BufferedReader in) {
        this.in = in;
        tryReadNext();
    }

    @Override
    public boolean hasNext() {
        return line != null;
    }

    @Override
    public String next() {
        String ret = line;
        tryReadNext();
        return ret;
    }

    private void tryReadNext() {
        try {
            line = in.readLine();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
