package io.nop.core.resource.impl;

import java.io.OutputStream;

public class OutputStreamResource extends AbstractResource {
    private final OutputStream out;

    public OutputStreamResource(String path, OutputStream out) {
        super(path);
        this.out = out;
    }

    @Override
    protected Object internalObj() {
        return out;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public OutputStream getOutputStream() {
        return out;
    }

    @Override
    public OutputStream getOutputStream(boolean append) {
        return out;
    }
}
