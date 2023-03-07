/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.js;

import io.nop.commons.io.stream.LogOutputStream;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestLogOutputStream {
    static final Logger LOG = LoggerFactory.getLogger(TestLogOutputStream.class);

    @Test
    public void testFlush() throws IOException {
        LogOutputStream out = new LogOutputStream(LOG, false);
        out.print("中问");
        out.flush();
        out.print("abc");
        out.flush();
        assertEquals(0, out.available());
    }
}
