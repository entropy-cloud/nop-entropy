package io.nop.ai.core.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import io.nop.commons.text.SourceCodeBlock;

public class TestCodeResponseParser {
    @Test
    public void testParse() {
        String text = "```python \n x=3;\n```\n";
        SourceCodeBlock block = CodeResponseParser.instance().parseResponse(text, "python");
        assertEquals("```python\n" +
                "x=3;\n" +
                "```\n", block.toMarkdown());
    }
}
