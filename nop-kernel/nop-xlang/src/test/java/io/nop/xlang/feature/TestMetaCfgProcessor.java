package io.nop.xlang.feature;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMetaCfgProcessor {
    @Test
    public void testParse() {
        IConfigProvider configProvider = AppConfig.getConfigProvider();
        Object processed = MetaCfgProcessor.processMetaCfg(configProvider, null, "@{a}");
        assertEquals("@{a}", processed);

        processed = MetaCfgProcessor.processMetaCfg(configProvider, null, "@meta-cfg:java.home");
        assertTrue(!processed.toString().startsWith("@meta-cfg:"));

        processed = MetaCfgProcessor.processMetaCfg(configProvider, null, "@meta-cfg:my.data|abc");
        assertEquals("abc", processed);

        processed = MetaCfgProcessor.processMetaCfg(configProvider, null, "@{@meta-cfg:my.data|123}_text");
        assertEquals("123_text", processed);
    }
}
