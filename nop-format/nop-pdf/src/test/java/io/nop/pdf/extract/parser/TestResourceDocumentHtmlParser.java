package io.nop.pdf.extract.parser;

import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.TocItem;
import io.nop.pdf.extract.struct.TocTable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 修复目录回读 off-by-one：写侧 href='#p'+pageNo（1-based），读侧按 0-based list 索引
 */
public class TestResourceDocumentHtmlParser {

    static final String HTML = "<html><head><title>demo</title></head><body>"
            + "<div class=\"pdf-toc\"><li><a href=\"#p2\">second</a></li></div>"
            + "<div class=\"pdf-page\" id=\"p1\" data-display-page-no=\"1\">"
            + "<div class=\"pdf-page-content\"><div class=\"pdf-line\" id=\"p1-0\">a</div></div></div>"
            + "<div class=\"pdf-page\" id=\"p2\" data-display-page-no=\"2\">"
            + "<div class=\"pdf-page-content\"><div class=\"pdf-line\" id=\"p2-0\">b</div></div></div>"
            + "</body></html>";

    @Test
    public void testTocPageIndexIsOneBased() {
        ResourceDocumentHtmlParser parser = new ResourceDocumentHtmlParser();
        ResourceDocument doc = parser.loadObjectFromResource(
                new ByteArrayResource("/test.html", HTML.getBytes(StandardCharsets.UTF_8), 0));

        assertEquals(2, doc.getPages().size());
        TocTable toc = doc.getTocTable();
        assertNotNull(toc);
        assertEquals(1, toc.getItems().size());
        TocItem item = toc.getItems().get(0);
        // href=#p2 指向第二页，displayPageNo=2（修复前 get(2) 越界/系统性错位一页）
        assertEquals(2, item.getPageNo());
        assertEquals("second", item.getTitle());
    }
}
