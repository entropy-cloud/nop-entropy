package io.nop.markdown;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.markdown.simple.MarkdownDocument;
import io.nop.markdown.simple.MarkdownDocumentParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestMarkdownDocument {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        MarkdownDocument tpl = new MarkdownDocumentParser().parseFromVirtualPath("/test/test.tpl.md");
        System.out.println(StringHelper.join(tpl.getAllFullTitles(), "\n"));
    }
}
