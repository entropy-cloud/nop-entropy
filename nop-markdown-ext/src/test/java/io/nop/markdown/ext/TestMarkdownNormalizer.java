package io.nop.markdown.ext;

import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestMarkdownNormalizer extends BaseTestCase {
    @Test
    public void testNormalize() {
        String text = "## `$`x$\\delta +1 $  \n\n\n\n ### some \n # value \n ````xml\n <a/> \n````\na\n$$\n \\alpha\n$$";
        String normalized = new MarkdownNormalizer().normalizeText(text);
        System.out.println(normalized);
        Assertions.assertEquals(normalizeCRLF(normalized).trim(), normalizeCRLF(attachmentText("normalized.md")).trim());
    }
}
