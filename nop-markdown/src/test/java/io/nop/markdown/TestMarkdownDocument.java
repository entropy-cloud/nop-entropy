package io.nop.markdown;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.markdown.simple.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestMarkdownDocument extends BaseTestCase {
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
        MarkdownDocument tpl = MarkdownTool.instance().parseFromVirtualPath("/test/test.tpl.md");
        System.out.println(StringHelper.join(tpl.getAllFullTitles(), "\n"));

        MarkdownDocument doc = MarkdownTool.instance().parseFromVirtualPath("/test/test.md");
        doc.matchTpl(tpl, true);

        assertNotNull(doc.findSectionByTitle("核心功能模块"));

        MarkdownDocument selected = doc.selectSectionByTplTag("MAIN", true);
        System.out.println(selected.toText(false));
    }

    @Test
    public void testSplit() {
        MarkdownDocument tpl = MarkdownTool.instance().parseFromVirtualPath("/test/test.md");
        File dir = new File(getTargetDir(), "split");
        tpl.splitToDir(dir, 1, null);
    }
}
