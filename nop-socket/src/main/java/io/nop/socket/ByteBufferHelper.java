/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.socket;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ByteBufferHelper {
    public static String readUtf8(ByteBuffer buf) {
        int len = buf.getInt();
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static byte[] readRemaining(ByteBuffer buf) {
        int n = buf.remaining();
        byte[] bytes = new byte[n];
        buf.get(bytes);
        return bytes;
    }
}
