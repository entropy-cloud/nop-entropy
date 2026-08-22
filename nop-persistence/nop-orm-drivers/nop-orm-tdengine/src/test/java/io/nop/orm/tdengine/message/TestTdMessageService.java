/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestTdMessageService {

    @Test
    public void testFailsLoudInsteadOfReturningNull() {
        TdMessageService service = new TdMessageService();
        // 修复前返回 null，调用方按非 null future 使用时会 NPE
        assertThrows(UnsupportedOperationException.class,
                () -> service.sendAsync("topic", "msg", null));
        assertThrows(UnsupportedOperationException.class,
                () -> service.subscribe("topic", (topic, message, context) -> null, null));
    }
}
