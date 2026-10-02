package io.nop.pdf.extract.parser;

import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.TextlineBlock;
import io.nop.pdf.extract.struct.TocTable;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 目录检测语义：起止符之间的"标题....页码"行必须解析出标题与页码；
 * 无目录结构返回null；结束符之后的行不再纳入
 */
public class TestSimpleTocDetector {

    private static TextlineBlock line(String content, double y, int pageNo) {
        TextlineBlock block = new TextlineBlock();
        block.setContent(content);
        block.setPageNo(pageNo);
        block.setViewBounding(new Rectangle2D.Double(0, y, 200, 10));
        return block;
    }

    private static ResourceDocument doc(TextlineBlock... lines) {
        ResourcePage page = new ResourcePage();
        page.setPageNo(3);
        for (TextlineBlock block : lines) {
            page.addTextline(block);
        }
        // 把 page.pageNo 回填到每个 block.pageNo
        page.resetAllBlockIndex();
        ResourceDocument doc = new ResourceDocument();
        List<ResourcePage> pages = new ArrayList<>();
        pages.add(page);
        doc.setPages(pages);
        return doc;
    }

    @Test
    public void testDetectTocItemsBetweenMarkers() {
        SimpleTocDetector detector = new SimpleTocDetector();
        detector.setStartMarker("目录");
        detector.setEndMarker("正文");

        TocTable toc = detector.detect(doc(
                line("目录", 0, 3),
                line("第一章 概述..........1", 20, 3),
                line("第二章 安装..........5", 40, 3),
                line("正文", 60, 3),
                line("不应纳入..........99", 80, 3)));

        assertNotNull(toc);
        assertEquals(2, toc.getItems().size());

        assertEquals("第一章 概述", toc.getItems().get(0).getTitle());
        assertEquals(1, toc.getItems().get(0).getPageNo());
        assertEquals(3, toc.getItems().get(0).getTocPageNo());

        assertEquals("第二章 安装", toc.getItems().get(1).getTitle());
        assertEquals(5, toc.getItems().get(1).getPageNo());

        // from/to 取目录项解析出的正文页码（非目录所在页码）
        assertEquals(1, toc.getFromPageNo());
        assertEquals(5, toc.getToPageNo());
    }

    @Test
    public void testDetectWithoutMarkersUsesPaddingHeuristic() {
        SimpleTocDetector detector = new SimpleTocDetector();
        // 无起始符时直接按"连续填充字符"识别候选行
        TocTable toc = detector.detect(doc(
                line("概 述..........1", 0, 3),
                line("安 装..........5", 20, 3)));

        assertNotNull(toc);
        assertEquals(2, toc.getItems().size());
        assertEquals("概 述", toc.getItems().get(0).getTitle());
    }

    @Test
    public void testLinesWithoutPaddingProduceNullToc() {
        SimpleTocDetector detector = new SimpleTocDetector();
        detector.setStartMarker("目录");
        detector.setEndMarker("正文");

        assertNull(detector.detect(doc(
                line("目录", 0, 3),
                line("普通正文行", 20, 3),
                line("正文", 40, 3))));
    }

    @Test
    public void testInsufficientPaddingCountIsRejected() {
        SimpleTocDetector detector = new SimpleTocDetector();
        detector.setStartMarker("目录");
        detector.setEndMarker("正文");
        // 两个点号低于默认 minPaddingCount=3
        TocTable toc = detector.detect(doc(
                line("目录", 0, 3),
                line("标题..1", 20, 3),
                line("正文", 40, 3)));
        assertNull(toc);
    }

    @Test
    public void testNonNumericPageNumberIsRejected() {
        SimpleTocDetector detector = new SimpleTocDetector();
        detector.setStartMarker("目录");
        detector.setEndMarker("正文");

        TocTable toc = detector.detect(doc(
                line("目录", 0, 3),
                line("标题.........abc", 20, 3),
                line("正文", 40, 3)));
        assertNull(toc);
    }

    @Test
    public void testPaddingCharConfigurable() {
        SimpleTocDetector detector = new SimpleTocDetector();
        detector.setStartMarker("目录");
        detector.setEndMarker("正文");
        detector.setPaddingChar('·');

        TocTable toc = detector.detect(doc(
                line("目录", 0, 3),
                line("第一章·····7", 20, 3),
                line("标题.........1", 40, 3),
                line("正文", 60, 3)));

        assertNotNull(toc);
        assertEquals(1, toc.getItems().size());
        assertEquals("第一章", toc.getItems().get(0).getTitle());
        assertEquals(7, toc.getItems().get(0).getPageNo());
    }
}
