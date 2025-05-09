package io.nop.ai.translate;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.commons.debug.DebugMessageHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class TestAiChatExchange extends BaseTestCase {
    @Test
    public void testChatResponse() {
        File file = getTestResourceFile("data/translate-result1.md");
        List<AiChatExchange> list = DebugMessageHelper.parseDebugFile(file);
        AiChatExchange response = list.get(0);
        String result = response.getBlock("<TRANSLATE_RESULT>\n", "\n</TRANSLATE_RESULT>", true, false);
        assertNotNull(result);
        assertTrue(response.isValid());
        assertFalse(response.getContent().contains("<TRANSLATE_RESULT>"));
    }
}
