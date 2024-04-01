package io.nop.markdown;

import org.junit.jupiter.api.Test;

public class TestMarkdownNormalizer {
    @Test
    public void testNormalize() {
        String text = "## data \n\n\n\n ### some \n # value";
        String normalized = new MarkdownNormalizer().normalizeText(text);
        System.out.println(normalized);
    }
}
