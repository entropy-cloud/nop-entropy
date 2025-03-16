package io.nop.ai.translate;

import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.commons.debug.DebugMessageHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAiChatResponse extends BaseTestCase {
    @Test
    public void testChatResponse(){
        File file = getTestResourceFile("data/translate-result1.md");
        List<AiChatResponse> list = DebugMessageHelper.parseDebugFile(file);
        AiChatResponse response = list.get(0);
        response.parseContentBlock("<TRANSLATE_RESULT>\n", "\n</TRANSLATE_RESULT>",true);
        assertTrue(response.isValid());
    }
}
