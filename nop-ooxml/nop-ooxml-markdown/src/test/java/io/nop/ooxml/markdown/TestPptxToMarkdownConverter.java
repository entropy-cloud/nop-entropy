package io.nop.ooxml.markdown;

import io.nop.core.resource.impl.FileResource;
import io.nop.markdown.simple.MarkdownDocument;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestPptxToMarkdownConverter {
    @Disabled
    @Test
    public void testConvert() {
        File file = new File("c:/test/test.pptx");
        PptxToMarkdownConverter converter = new PptxToMarkdownConverter();
        converter.imagesDirPath("c:/test/images");
        MarkdownDocument doc = converter.convertFromResource(new FileResource(file));

        System.out.println(doc.toText());
    }
}
