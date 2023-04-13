package io.nop.log.logback;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class TestLogSessionId {
    static final Logger LOG = LoggerFactory.getLogger(TestLogSessionId.class);

    @Test
    public void testLog() {
        MDC.put("sessionId", "s123456");
        LOG.info("***********test:{}", "sss");
        MDC.remove("sessionId");
    }
}
