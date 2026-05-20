/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestDebeziumCdcSourceFunction {

    @Test
    void testNullConfigRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DebeziumCdcSourceFunction(null));
    }

    @Test
    void testCancelBeforeRun() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("test");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);
        assertDoesNotThrow(source::cancel);
    }
}
