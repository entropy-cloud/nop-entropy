package io.nop.ai.core.response;

import io.nop.markdown.simple.MarkdownCodeBlock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCodeResponseParser {
    @Test
    public void testParse() {
        String text = "```python \n x=3;\n```\n";
        MarkdownCodeBlock block = CodeResponseParser.instance().parseResponse(text, "python");
        assertEquals("```python\n\n" +
                "x=3;\n" +
                "```\n", block.toText());
    }
}
