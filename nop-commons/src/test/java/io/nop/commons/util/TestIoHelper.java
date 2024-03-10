/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestIoHelper {
    @Test
    public void testSerialize() {
        byte[] data = IoHelper.serializeToByteArray(Collections.emptyMap());
        Object o = IoHelper.deserializeFromByteArray(data);
        assertEquals(o, Collections.emptyMap());
    }

}
