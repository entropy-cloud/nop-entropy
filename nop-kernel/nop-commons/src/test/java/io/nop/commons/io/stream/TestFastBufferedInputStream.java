/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.stream;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestFastBufferedInputStream {
    @Test
    public void testMark() throws IOException {
        int n = 11900;
        ByteArrayInputStream is = newByteStream(n);
        is.mark(2);
        is.read(new byte[2]);
        is.reset();

        is.mark(100);
        byte[] bytes = new byte[200];
        assertEquals(200, is.read(bytes));
        assertEquals(0, bytes[0]);
        is.reset();

        for (int i = 0; i < n; i++) {
            assertEquals(i % 9, is.read());
        }
        assertEquals(-1, is.read());
    }

    ByteArrayInputStream newByteStream(int n) {
        return new ByteArrayInputStream(buildBytes(n));
    }

    byte[] buildBytes(int n) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (int i = 0; i < n; i++) {
            buf.write(i % 9);
        }
        return buf.toByteArray();
    }
}
