/*
 * Copyright (c) 2025, Entropy Cloud
 *
 * Licensed to the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.ai.shell.executor;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Tee output stream that writes to two output streams simultaneously.
 * This is used for stream merging operations like 2>&1 or &>file.
 */
public class TeeOutputStream extends OutputStream {

    private final OutputStream out1;
    private final OutputStream out2;

    /**
     * Creates a new TeeOutputStream that writes to both output streams.
     *
     * @param out1 the first output stream
     * @param out2 the second output stream
     */
    public TeeOutputStream(OutputStream out1, OutputStream out2) {
        this.out1 = out1;
        this.out2 = out2;
    }

    @Override
    public void write(int b) throws IOException {
        out1.write(b);
        out2.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out1.write(b, off, len);
        out2.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        out1.flush();
        out2.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            out1.close();
        } finally {
            out2.close();
        }
    }
}
