/*

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */
package io.nop.svg.parse;

import java.io.IOException;
import java.io.Reader;

/**
 * This class represents a reader which normalizes the line break: \n, \r, \r\n are replaced by \n. The methods of this
 * reader are not synchronized. The input is buffered.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id$
 */
public class NormalizingReader extends Reader {

    /**
     * The next char.
     */
    protected int nextChar = -1;

    /**
     * The current line in the stream.
     */
    protected int line = 1;

    /**
     * The current column in the stream.
     */
    protected int column;

    final Reader reader;

    public NormalizingReader(Reader reader) {
        this.reader = reader;
    }

    /**
     * Read characters into a portion of an array.
     *
     * @param cbuf Destination buffer
     * @param off  Offset at which to start writing characters
     * @param len  Maximum number of characters to read
     * @return The number of characters read, or -1 if the end of the stream has been reached
     */
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }

        int c = read();
        if (c == -1) {
            return -1;
        }
        int result = 0;
        do {
            cbuf[result + off] = (char) c;
            result++;
            c = read();
        } while (c != -1 && result < len);
        return result;
    }

    /**
     * Returns the current line in the stream.
     */
    public int getLine() {
        return line;
    }

    /**
     * Returns the current column in the stream.
     */
    public int getColumn() {
        return column;
    }

    /**
     * Read a single character. This method will block until a character is available, an I/O error occurs, or the end
     * of the stream is reached.
     */
    public int read() throws IOException {
        int result = nextChar;
        if (result != -1) {
            nextChar = -1;
            if (result == 13) {
                column = 0;
                line++;
            } else {
                column++;
            }
            return result;
        }
        result = reader.read();
        switch (result) {
            case 13:
                column = 0;
                line++;
                int c = reader.read();
                if (c == 10) {
                    return 10;
                }
                nextChar = c;
                return 10;

            case 10:
                column = 0;
                line++;
        }
        return result;
    }

    public void close() throws IOException {
        reader.close();
    }
}
