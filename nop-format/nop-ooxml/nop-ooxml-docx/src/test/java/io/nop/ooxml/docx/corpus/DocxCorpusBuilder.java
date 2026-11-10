package io.nop.ooxml.docx.corpus;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

/**
 * Programmatically synthesizes complex sample .docx packages used as the docx corpus
 * for the word-document-model roadmap (parser/writer/round-trip verification).
 *
 * <p>Deliberately independent of {@code OfficeDocModelWriter}: the corpus must not be
 * produced by the code under test (no self-referential verification). Parts are written
 * as minimal hand-authored OOXML XML and zipped directly.</p>
 *
 * <p><b>Determinism contract</b> (downstream tests consume the corpus by calling these
 * builder methods directly, regenerating inside each test): output contains no timestamps,
 * no random ids and no system paths — image and relationship ids are fixed sequence
 * numbers and every zip entry carries the same fixed modification time, so two invocations
 * of the same builder method return byte-identical packages.</p>
 */
public final class DocxCorpusBuilder {

    /**
     * Fixed zip entry modification time (2026-01-01T00:00:00Z) — required for the
     * byte-identical determinism contract; {@link ZipEntry} defaults to the current time.
     */
    static final long FIXED_ZIP_TIME = 1767225600000L;

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String RELS_NS = "http://schemas.openxmlformats.org/package/2006/relationships";

    private static final String CT_MAIN = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String CT_STYLES = "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml";
    private static final String CT_NUMBERING = "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml";
    private static final String CT_SETTINGS = "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml";
    private static final String CT_HEADER = "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml";
    private static final String CT_FOOTER = "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml";
    private static final String CT_COMMENTS = "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml";

    private static final String REL_TYPE_IMAGE = R_NS + "/image";
    private static final String REL_TYPE_HYPERLINK = R_NS + "/hyperlink";
    private static final String REL_TYPE_HEADER = R_NS + "/header";
    private static final String REL_TYPE_FOOTER = R_NS + "/footer";
    private static final String REL_TYPE_STYLES = R_NS + "/styles";
    private static final String REL_TYPE_NUMBERING = R_NS + "/numbering";
    private static final String REL_TYPE_SETTINGS = R_NS + "/settings";
    private static final String REL_TYPE_COMMENTS = R_NS + "/comments";

    /** A4 portrait with 2.5cm margins + header/footer distances. */
    private static final String PAGE_SETUP_PORTRAIT =
            "<w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                    + "<w:pgMar w:top=\"1417\" w:right=\"1417\" w:bottom=\"1417\" w:left=\"1417\" "
                    + "w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>";

    private static final String PAGE_SETUP_LANDSCAPE =
            "<w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/>"
                    + "<w:pgMar w:top=\"1417\" w:right=\"1417\" w:bottom=\"1417\" w:left=\"1417\" "
                    + "w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>";

    private static final String DEFAULT_STYLES_XML = XML_DECL
            + "<w:styles xmlns:w=\"" + W_NS + "\">"
            + "<w:docDefaults><w:rPrDefault><w:rPr>"
            + "<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/><w:sz w:val=\"22\"/>"
            + "</w:rPr></w:rPrDefault></w:docDefaults>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/>"
            + "<w:pPr><w:jc w:val=\"center\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"56\"/></w:rPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/>"
            + "<w:pPr><w:outlineLvl w:val=\"0\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"32\"/>"
            + "<w:color w:val=\"2E74B5\"/></w:rPr></w:style>"
            + "</w:styles>";

    private static final String DEFAULT_NUMBERING_XML = XML_DECL
            + "<w:numbering xmlns:w=\"" + W_NS + "\">"
            + "<w:abstractNum w:abstractNumId=\"0\">"
            + "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/>"
            + "<w:lvlText w:val=\"%1.\"/><w:lvlJc w:val=\"left\"/></w:lvl>"
            + "<w:lvl w:ilvl=\"1\"><w:start w:val=\"1\"/><w:numFmt w:val=\"lowerLetter\"/>"
            + "<w:lvlText w:val=\"%2)\"/><w:lvlJc w:val=\"left\"/></w:lvl>"
            + "</w:abstractNum>"
            + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>"
            + "</w:numbering>";

    private DocxCorpusBuilder() {
    }

    /**
     * 基线样本：两个普通段落（居中 + 缩进），单节，无页眉页脚。
     */
    public static byte[] simpleDocument() {
        Assembly asm = new Assembly();
        asm.p(paragraph("<w:jc w:val=\"center\"/>", run("Hello Nop docx corpus.", null)));
        asm.p(paragraph("<w:ind w:left=\"360\" w:firstLine=\"240\"/>", run("Second indented paragraph.", null)));
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        return asm.build();
    }

    /**
     * 标题 + 多级列表：Title / Heading1 样式段落 + 两级编号列表（numbering 部件）。
     */
    public static byte[] titleAndListDoc() {
        Assembly asm = new Assembly();
        asm.p(paragraph("<w:pStyle w:val=\"Title\"/>", run("Report Title", null)));
        asm.p(paragraph("<w:pStyle w:val=\"Heading1\"/>", run("Chapter One", null)));
        asm.p(paragraph("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>",
                run("first level item", null)));
        asm.p(paragraph("<w:numPr><w:ilvl w:val=\"1\"/><w:numId w:val=\"1\"/></w:numPr>",
                run("second level item", null)));
        asm.p(paragraph("<w:pStyle w:val=\"Heading1\"/>", run("Chapter Two", null)));
        asm.p(paragraph("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>",
                run("another first level item", null)));
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        asm.numberingPart();
        return asm.build();
    }

    /**
     * 图片块：drawing/inline 图 + 关系部件 + 媒体部件（确定性 PNG）。
     */
    public static byte[] imageDoc() {
        Assembly asm = new Assembly();
        asm.p(paragraph(null, run("Picture below:", null)));
        asm.raw("<w:p><w:r><w:drawing>"
                + "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\" "
                + "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\">"
                + "<wp:extent cx=\"1905000\" cy=\"952500\"/>"
                + "<wp:docPr id=\"1\" name=\"corpus-picture\"/>"
                + "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
                + "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:nvPicPr><pic:cNvPr id=\"1\" name=\"corpus.png\"/><pic:cNvPicPr/></pic:nvPicPr>"
                + "<pic:blipFill><a:blip r:embed=\"rIdImg1\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
                + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"1905000\" cy=\"952500\"/></a:xfrm>"
                + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>"
                + "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
        asm.p(paragraph(null, run("After picture.", null)));
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        asm.rel("rIdImg1", REL_TYPE_IMAGE, "media/image1.png", null);
        asm.binaryPart("word/media/image1.png", deterministicPng());
        return asm.build();
    }

    /**
     * 独立超链接块：w:hyperlink 块级元素 + 外部关系（不被解包进普通 run 的形态）。
     */
    public static byte[] hyperlinkDoc() {
        Assembly asm = new Assembly();
        asm.raw("<w:hyperlink r:id=\"rIdLink1\"><w:r>"
                + "<w:rPr><w:color w:val=\"0563C1\"/><w:u w:val=\"single\"/></w:rPr>"
                + "<w:t>corpus hyperlink</w:t></w:r></w:hyperlink>");
        asm.p(paragraph(null, run("After hyperlink.", null)));
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        asm.rel("rIdLink1", REL_TYPE_HYPERLINK, "https://nop.example.com/corpus", "External");
        return asm.build();
    }

    /**
     * 行内 tab 与换行 br：单段落内 text → tab → text → br → text。
     */
    public static byte[] inlineTabBreakDoc() {
        Assembly asm = new Assembly();
        asm.raw("<w:p><w:r><w:t>before tab</w:t></w:r>"
                + "<w:r><w:tab/></w:r>"
                + "<w:r><w:t>after tab</w:t></w:r>"
                + "<w:r><w:br/></w:r>"
                + "<w:r><w:t>after line break</w:t></w:r></w:p>");
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        return asm.build();
    }

    /**
     * 分页与分隔线：显式 page break 段落 + 段落底边框分隔线段落。
     */
    public static byte[] pageBreakAndSeparatorDoc() {
        Assembly asm = new Assembly();
        asm.p(paragraph(null, run("Page one content.", null)));
        asm.raw("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
        asm.p(paragraph(null, run("Page two content.", null)));
        asm.raw("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"808080\"/>"
                + "</w:pBdr></w:pPr></w:p>");
        asm.p(paragraph(null, run("After separator.", null)));
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        return asm.build();
    }

    /**
     * 多节页眉页脚：两节，每节独立 header/footer 部件（header1/footer1 + header2/footer2）；
     * 第一节纵向 A4，第二节横向 A4。第一节 sectPr 内嵌于节末段落 pPr，第二节 sectPr 位于 body 末尾。
     */
    public static byte[] multiSectionDoc() {
        Assembly asm = new Assembly();
        asm.p(paragraph(null, run("Section one body.", null)));
        // section break: first section's sectPr is embedded in the last paragraph of that section
        asm.raw("<w:p><w:pPr><w:sectPr>"
                + "<w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:headerReference w:type=\"default\" r:id=\"rIdHdr1\"/>"
                + "<w:footerReference w:type=\"default\" r:id=\"rIdFtr1\"/>"
                + "</w:sectPr></w:pPr></w:p>");
        asm.p(paragraph(null, run("Section two body.", null)));
        asm.sectPr(PAGE_SETUP_LANDSCAPE
                + "<w:headerReference w:type=\"default\" r:id=\"rIdHdr2\"/>"
                + "<w:footerReference w:type=\"default\" r:id=\"rIdFtr2\"/>");

        asm.rel("rIdHdr1", REL_TYPE_HEADER, "header1.xml", null);
        asm.rel("rIdFtr1", REL_TYPE_FOOTER, "footer1.xml", null);
        asm.rel("rIdHdr2", REL_TYPE_HEADER, "header2.xml", null);
        asm.rel("rIdFtr2", REL_TYPE_FOOTER, "footer2.xml", null);
        asm.xmlOverride("word/header1.xml",
                XML_DECL + "<w:hdr xmlns:w=\"" + W_NS + "\"><w:p>" + run("Header One", null)
                        + "</w:p></w:hdr>", CT_HEADER);
        asm.xmlOverride("word/footer1.xml",
                XML_DECL + "<w:ftr xmlns:w=\"" + W_NS + "\"><w:p>" + run("Footer One", null)
                        + "</w:p></w:ftr>", CT_FOOTER);
        asm.xmlOverride("word/header2.xml",
                XML_DECL + "<w:hdr xmlns:w=\"" + W_NS + "\"><w:p>" + run("Header Two", null)
                        + "</w:p></w:hdr>", CT_HEADER);
        asm.xmlOverride("word/footer2.xml",
                XML_DECL + "<w:ftr xmlns:w=\"" + W_NS + "\"><w:p>" + run("Footer Two", null)
                        + "</w:p></w:ftr>", CT_FOOTER);
        return asm.build();
    }

    /**
     * 合并表格：2x3 表格含 gridSpan（横向合并）、vMerge restart/continue（纵向合并）、
     * 列宽、行高、单元格底纹、边框与垂直对齐。
     */
    public static byte[] mergedTableDoc() {
        Assembly asm = new Assembly();
        asm.raw("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>"
                + "<w:top w:val=\"single\" w:sz=\"4\" w:color=\"333333\"/>"
                + "<w:left w:val=\"single\" w:sz=\"4\" w:color=\"333333\"/>"
                + "<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"333333\"/>"
                + "<w:right w:val=\"single\" w:sz=\"4\" w:color=\"333333\"/>"
                + "<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"333333\"/>"
                + "<w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"333333\"/>"
                + "</w:tblBorders></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"3000\"/><w:gridCol w:w=\"3000\"/><w:gridCol w:w=\"3000\"/></w:tblGrid>"
                // row 1: horizontally merged cell (gridSpan=2) + normal cell
                + "<w:tr><w:trPr><w:trHeight w:val=\"400\" w:hRule=\"atLeast\"/></w:trPr>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"6000\" w:type=\"dxa\"/><w:gridSpan w:val=\"2\"/>"
                + "<w:shd w:val=\"clear\" w:fill=\"DCE6F1\"/><w:vAlign w:val=\"center\"/></w:tcPr>"
                + "<w:p>" + run("merged across", null) + "</w:p></w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p>" + run("r1c3", null) + "</w:p></w:tc></w:tr>"
                // row 2/3: vertically merged first column (restart + continue)
                + "<w:tr><w:trPr><w:trHeight w:val=\"360\" w:hRule=\"atLeast\"/></w:trPr>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/><w:vMerge w:val=\"restart\"/></w:tcPr>"
                + "<w:p>" + run("merged down", null) + "</w:p></w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p>" + run("r2c2", null) + "</w:p></w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p>" + run("r2c3", null) + "</w:p></w:tc></w:tr>"
                + "<w:tr><w:trPr><w:trHeight w:val=\"360\" w:hRule=\"atLeast\"/></w:trPr>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/><w:vMerge/></w:tcPr>"
                + "<w:p/></w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p>" + run("r3c2", null) + "</w:p></w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p>" + run("r3c3", null) + "</w:p></w:tc></w:tr>"
                + "</w:tbl>");
        asm.p(paragraph(null, run("After table.", null)));
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        return asm.build();
    }

    /**
     * run 全样式：同一行内覆盖粗体/斜体/下划线/删除线/高亮/上下标/字体/字号/颜色。
     */
    public static byte[] fullRunStyleDoc() {
        Assembly asm = new Assembly();
        asm.p(paragraph(null,
                run("bold", "<w:b/>")
                        + run(" italic", "<w:i/>")
                        + run(" underlined", "<w:u w:val=\"single\"/>")
                        + run(" struck", "<w:strike/>")
                        + run(" highlighted", "<w:highlight w:val=\"yellow\"/>")
                        + run(" super", "<w:vertAlign w:val=\"superscript\"/>")
                        + run(" sub", "<w:vertAlign w:val=\"subscript\"/>")));
        asm.p(paragraph(null,
                run("font+size+color", "<w:rFonts w:ascii=\"Arial\"/><w:sz w:val=\"36\"/>"
                        + "<w:color w:val=\"C00000\"/><w:b/>")));
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        return asm.build();
    }

    /**
     * 标题块 c:for：begin/end 标记锚定在 Title 样式段落上 -> title.model loop 字段。
     */
    public static byte[] titleLoopDoc() {
        Assembly asm = new Assembly();
        String tForBeg = "xpl:" + pct("<c:for var=\"sec\" items=\"${order.sections.subList(0,2)}\">");
        String tForEnd = "xpl:" + pct("</c:for>");
        asm.rel("rIdTtlForBeg", REL_TYPE_HYPERLINK, tForBeg, "External");
        asm.rel("rIdTtlForEnd", REL_TYPE_HYPERLINK, tForEnd, "External");
        asm.p(paragraph("<w:pStyle w:val=\"Heading1\"/>",
                tplLink("rIdTtlForBeg", "ttl-for-begin")
                        + run("Repeated title.", null)
                        + tplLink("rIdTtlForEnd", "ttl-for-end")));
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        return asm.build();
    }

    /**
     * 混合 run：单个 run 内 text → br → text（Word 软换行常见形态），
     * 解析必须保留文本且对不可分派的 br 具名 skip。
     */
    public static byte[] mixedBreakDoc() {
        Assembly asm = new Assembly();
        asm.raw("<w:p><w:r><w:t>before break</w:t><w:br/><w:t>after break</w:t></w:r></w:p>");
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        return asm.build();
    }

    /**
     * 页面设置：横向 A4 + 显式页边距（与 portrait 样本形成对照）。
     */
    public static byte[] pageSetupDoc() {
        Assembly asm = new Assembly();
        asm.p(paragraph(null, run("Landscape page setup sample.", null)));
        asm.sectPr(PAGE_SETUP_LANDSCAPE);
        return asm.build();
    }

    /**
     * 背景 + 水印：document 背景（w:background + settings displayBackgroundShape）
     * 与页眉 VML 文本水印。
     */
    public static byte[] backgroundWatermarkDoc() {
        Assembly asm = new Assembly();
        asm.background = "<w:background w:color=\"EEF7FF\"/>";
        asm.p(paragraph(null, run("Background and watermark sample.", null)));
        asm.sectPr(PAGE_SETUP_PORTRAIT
                + "<w:headerReference w:type=\"default\" r:id=\"rIdHdr1\"/>");
        asm.settingsXml = XML_DECL + "<w:settings xmlns:w=\"" + W_NS + "\">"
                + "<w:displayBackgroundShape/></w:settings>";
        asm.rel("rIdHdr1", REL_TYPE_HEADER, "header1.xml", null);
        asm.xmlOverride("word/header1.xml", XML_DECL
                + "<w:hdr xmlns:w=\"" + W_NS + "\" xmlns:v=\"urn:schemas-microsoft-com:vml\" "
                + "xmlns:o=\"urn:schemas-microsoft-com:office:office\">"
                + "<w:p><w:r><w:rPr/><w:pict>"
                + "<v:shape id=\"WaterMark1\" style=\"position:absolute;margin-left:0;margin-top:0;"
                + "width:400pt;height:200pt;rotation:315;z-index:-251654144\" o:allowincell=\"f\" "
                + "fillcolor=\"silver\">"
                + "<v:textpath style=\"font-family:&quot;Calibri&quot;;font-size:1pt\" string=\"CORPUS WATERMARK\"/>"
                + "</v:shape></w:pict></w:r></w:p></w:hdr>", CT_HEADER);
        return asm.build();
    }

    /**
     * 模板标注样本（WI4）：c:if 段落、c:for 普通段落（loop 落 paragraph.model）、
     * c:for 列表项（复杂 items 表达式，loop 落 list.model）、expr:/xpl:/tpl_expr:
     * 模板超链接各一、xpt:table 批注顶层表，以及一个「批注仅锚定在嵌套表内」的
     * 外层表（负向样本：外层表不得被标记）。另含正文段落内联批注标记
     * （commentRangeStart/End + commentReference）。模板超链接形态对齐真实 docx：
     * 关系 Target 为 percent-encode 的 xpl:/expr: + TargetMode="External"。
     * 注释固定 id，无时间戳，确定性输出。
     */
    public static byte[] templateAnnotationDoc() {
        Assembly asm = new Assembly();

        // template marker relationship targets (percent-encoded, TargetMode=External),
        // mirroring the form Word writes into word/_rels/document.xml.rels
        String tIfBeg = "xpl:" + pct("<c:if test=\"${entity.active}\">");
        String tIfEnd = "xpl:" + pct("</c:if>");
        String tForBeg = "xpl:" + pct("<c:for var=\"item\" items=\"${order.items}\" index=\"idx\">");
        String tForEnd = "xpl:" + pct("</c:for>");
        String tListForBeg = "xpl:" + pct("<c:for var=\"line\" items=\"${order.lines.subList(0,2)}\">");
        String tExpr = "expr:user.name";
        String tTplExpr = pct("xpl:Hello ${user.name}!");
        String tXpl = pct("xpl:<c:out value=\"${user.title}\"/>");

        // 1. c:if paragraph: markers live in one paragraph -> paragraph.model.testExpr
        asm.p(paragraph(null,
                tplLink("rIdIfBeg", "if-begin")
                        + run("Conditional paragraph.", null)
                        + tplLink("rIdIfEnd", "if-end")));

        // 2. c:for paragraph with simple items path -> paragraph.model loop fields
        asm.p(paragraph(null,
                tplLink("rIdForBeg", "for-begin")
                        + run("Repeated paragraph.", null)
                        + tplLink("rIdForEnd", "for-end")));

        // 3. c:for on a list item with a complex items expression -> list.model.beginLoop
        asm.p(paragraph("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>",
                tplLink("rIdListForBeg", "list-for-begin")
                        + run("Repeated list item.", null)
                        + tplLink("rIdListForEnd", "list-for-end")));
        asm.p(paragraph("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>",
                run("Plain list item.", null)));

        // 4. one paragraph with expr: / tpl_expr: / xpl: template hyperlinks
        asm.p(paragraph(null,
                run("Links:", null)
                        + tplLink("rIdExpr", "expr link")
                        + tplLink("rIdTplExpr", "tpl link")
                        + tplLink("rIdXpl", "xpl link")));

        // template marker relationships
        asm.rel("rIdIfBeg", REL_TYPE_HYPERLINK, tIfBeg, "External");
        asm.rel("rIdIfEnd", REL_TYPE_HYPERLINK, tIfEnd, "External");
        asm.rel("rIdForBeg", REL_TYPE_HYPERLINK, tForBeg, "External");
        asm.rel("rIdForEnd", REL_TYPE_HYPERLINK, tForEnd, "External");
        asm.rel("rIdListForBeg", REL_TYPE_HYPERLINK, tListForBeg, "External");
        asm.rel("rIdListForEnd", REL_TYPE_HYPERLINK, tForEnd, "External");
        asm.rel("rIdExpr", REL_TYPE_HYPERLINK, tExpr, "External");
        asm.rel("rIdTplExpr", REL_TYPE_HYPERLINK, tTplExpr, "External");
        asm.rel("rIdXpl", REL_TYPE_HYPERLINK, tXpl, "External");

        // 5. inline comment markers in a body paragraph (must not become unknown skips
        //    nor an extra empty text run)
        asm.raw("<w:p><w:commentRangeStart w:id=\"2\"/>" + run("Commented text.", null)
                + "<w:commentRangeEnd w:id=\"2\"/>" + commentRefRun("2") + "</w:p>");

        // 6. xpt:table annotated top-level table (first row first cell carries the comment)
        asm.raw("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"3000\"/><w:gridCol w:w=\"3000\"/></w:tblGrid>"
                + "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p><w:commentRangeStart w:id=\"0\"/>" + run("Name", null)
                + "<w:commentRangeEnd w:id=\"0\"/>" + commentRefRun("0") + "</w:p></w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p>" + run("Title", null) + "</w:p></w:tc></w:tr>"
                + "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p>" + run("first", null) + "</w:p></w:tc>"
                + "<w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p>" + run("row", null) + "</w:p></w:tc></w:tr></w:tbl>");

        asm.p(paragraph(null, run("Between tables.", null)));

        // 7. negative sample: the xpt:table comment is anchored only inside a nested
        //    table of the first cell; the outer (top-level) table must NOT be flagged
        asm.raw("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"6000\"/></w:tblGrid>"
                + "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"6000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p>" + run("outer first cell", null) + "</w:p>"
                + "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"3000\"/></w:tblGrid>"
                + "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p><w:commentRangeStart w:id=\"1\"/>" + run("nested cell", null)
                + "<w:commentRangeEnd w:id=\"1\"/>" + commentRefRun("1") + "</w:p></w:tc></w:tr>"
                + "</w:tbl>"
                + "<w:p/></w:tc></w:tr></w:tbl>");

        asm.p(paragraph(null, run("After tables.", null)));

        asm.sectPr(PAGE_SETUP_PORTRAIT);
        asm.numberingPart();
        asm.commentsPart(
                new String[]{"0", "xpt:table"},
                new String[]{"1", "xpt:table nested (inside nested table)"},
                new String[]{"2", "review note"});
        return asm.build();
    }

    /**
     * 悬空批注引用样本：表格首单元格 commentReference 指向不存在的批注 id
     * （comments 部件存在但不含该 id）→ 不标记表格且解析记具名 skip。
     */
    public static byte[] danglingCommentReferenceDoc() {
        Assembly asm = new Assembly();
        asm.raw("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr>"
                + "<w:tblGrid><w:gridCol w:w=\"3000\"/></w:tblGrid>"
                + "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"3000\" w:type=\"dxa\"/></w:tcPr>"
                + "<w:p>" + run("Name", null) + commentRefRun("9") + "</w:p></w:tc></w:tr></w:tbl>");
        asm.p(paragraph(null, run("After table.", null)));
        asm.sectPr(PAGE_SETUP_PORTRAIT);
        asm.commentsPart(new String[]{"0", "unrelated comment"});
        return asm.build();
    }

    /**
     * Percent-encodes a template marker target the way Word stores template
     * hyperlinks in document.xml.rels (e.g. xpl:%3cc:for%20...%3e).
     */
    private static String pct(String source) {
        return source.replace("<", "%3c").replace(">", "%3e")
                .replace("\"", "%22").replace(" ", "%20")
                .replace("{", "%7b").replace("}", "%7d");
    }

    /**
     * Template marker hyperlink; the template semantics live in the relationship
     * Target (xpl:/expr:, percent-encoded) registered under the same id.
     */
    private static String tplLink(String relId, String label) {
        return "<w:hyperlink r:id=\"" + relId + "\" w:history=\"1\">"
                + run(label, null) + "</w:hyperlink>";
    }

    private static String commentRefRun(String id) {
        return "<w:r><w:rPr><w:rStyle w:val=\"CommentReference\"/></w:rPr>"
                + "<w:commentReference w:id=\"" + id + "\"/></w:r>";
    }

    // ------------------------------------------------------------------ plumbing

    private static final class Part {
        final String path;
        final byte[] bytes;
        final String contentType; // null => no Override entry

        Part(String path, byte[] bytes, String contentType) {
            this.path = path;
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }

    private static final class Rel {
        final String id;
        final String type;
        final String target;
        final String targetMode; // null => internal

        Rel(String id, String type, String target, String targetMode) {
            this.id = id;
            this.type = type;
            this.target = target;
            this.targetMode = targetMode;
        }
    }

    /**
     * Mutable assembly state for one sample package; {@link #build()} freezes it into
     * a deterministic zip.
     */
    private static final class Assembly {
        final StringBuilder blocks = new StringBuilder();
        final List<Part> parts = new ArrayList<>();
        final List<Rel> rels = new ArrayList<>();
        String sectPr = "";
        String background = "";
        String settingsXml = null;

        void p(String paragraphXml) {
            blocks.append(paragraphXml);
        }

        void raw(String xml) {
            blocks.append(xml);
        }

        void sectPr(String inner) {
            this.sectPr = inner;
        }

        void numberingPart() {
            xmlOverride("word/numbering.xml", DEFAULT_NUMBERING_XML, CT_NUMBERING);
            rel("rIdNumbering", REL_TYPE_NUMBERING, "numbering.xml", null);
        }

        /**
         * WI4: comments part (word/comments.xml) with fixed id -> text pairs;
         * adds the Content-Type override and the document relationship.
         */
        void commentsPart(String[]... idTextPairs) {
            StringBuilder sb = new StringBuilder(XML_DECL)
                    .append("<w:comments xmlns:w=\"").append(W_NS).append("\">");
            for (String[] pair : idTextPairs) {
                sb.append("<w:comment w:id=\"").append(pair[0]).append("\" w:author=\"corpus\"")
                        .append(" w:initials=\"C\"><w:p>").append(run(pair[1], null))
                        .append("</w:p></w:comment>");
            }
            sb.append("</w:comments>");
            xmlOverride("word/comments.xml", sb.toString(), CT_COMMENTS);
            rel("rIdComments", REL_TYPE_COMMENTS, "comments.xml", null);
        }

        void rel(String id, String type, String target, String targetMode) {
            rels.add(new Rel(id, type, target, targetMode));
        }

        void xmlOverride(String path, String xml, String contentType) {
            parts.add(new Part(path, xml.getBytes(StandardCharsets.UTF_8), contentType));
        }

        void binaryPart(String path, byte[] bytes) {
            parts.add(new Part(path, bytes, null));
        }

        byte[] build() {
            // fixed part order for byte-identical output
            List<Part> ordered = new ArrayList<>();
            ordered.add(new Part("[Content_Types].xml",
                    contentTypes().getBytes(StandardCharsets.UTF_8), null));
            ordered.add(new Part("_rels/.rels", rootRels().getBytes(StandardCharsets.UTF_8), null));
            if (settingsXml != null)
                ordered.add(new Part("word/settings.xml",
                        settingsXml.getBytes(StandardCharsets.UTF_8), CT_SETTINGS));
            ordered.add(new Part("word/styles.xml",
                    DEFAULT_STYLES_XML.getBytes(StandardCharsets.UTF_8), CT_STYLES));
            ordered.add(new Part("word/document.xml",
                    documentXml().getBytes(StandardCharsets.UTF_8), CT_MAIN));
            ordered.add(new Part("word/_rels/document.xml.rels",
                    documentRels().getBytes(StandardCharsets.UTF_8), null));
            ordered.addAll(parts);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                ZipOutputStream zip = new ZipOutputStream(out);
                for (Part part : ordered) {
                    ZipEntry entry = new ZipEntry(part.path);
                    entry.setTime(FIXED_ZIP_TIME);
                    zip.putNextEntry(entry);
                    zip.write(part.bytes);
                    zip.closeEntry();
                }
                zip.finish();
                zip.close();
                return out.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("failed to assemble corpus docx", e);
            }
        }

        private String documentXml() {
            return XML_DECL
                    + "<w:document xmlns:w=\"" + W_NS + "\" xmlns:r=\"" + R_NS + "\">"
                    + background
                    + "<w:body>"
                    + blocks
                    + (sectPr.isEmpty() ? "" : "<w:sectPr>" + sectPr + "</w:sectPr>")
                    + "</w:body></w:document>";
        }

        private String documentRels() {
            StringBuilder sb = new StringBuilder(XML_DECL)
                    .append("<Relationships xmlns=\"").append(RELS_NS).append("\">");
            sb.append("<Relationship Id=\"rIdStyles\" Type=\"").append(REL_TYPE_STYLES)
                    .append("\" Target=\"styles.xml\"/>");
            if (settingsXml != null)
                sb.append("<Relationship Id=\"rIdSettings\" Type=\"").append(REL_TYPE_SETTINGS)
                        .append("\" Target=\"settings.xml\"/>");
            for (Rel rel : rels) {
                sb.append("<Relationship Id=\"").append(rel.id).append("\" Type=\"").append(rel.type)
                        .append("\" Target=\"").append(rel.target).append("\"");
                if (rel.targetMode != null)
                    sb.append(" TargetMode=\"").append(rel.targetMode).append("\"");
                sb.append("/>");
            }
            sb.append("</Relationships>");
            return sb.toString();
        }

        private String rootRels() {
            return XML_DECL + "<Relationships xmlns=\"" + RELS_NS + "\">"
                    + "<Relationship Id=\"rId1\" Type=\"" + R_NS + "/officeDocument\" "
                    + "Target=\"word/document.xml\"/></Relationships>";
        }

        private String contentTypes() {
            StringBuilder sb = new StringBuilder(XML_DECL)
                    .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                    .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                    .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                    .append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
                    .append("<Override PartName=\"/word/document.xml\" ContentType=\"").append(CT_MAIN).append("\"/>")
                    .append("<Override PartName=\"/word/styles.xml\" ContentType=\"").append(CT_STYLES).append("\"/>");
            if (settingsXml != null)
                sb.append("<Override PartName=\"/word/settings.xml\" ContentType=\"").append(CT_SETTINGS).append("\"/>");
            for (Part part : parts) {
                if (part.contentType != null)
                    sb.append("<Override PartName=\"/" + part.path + "\" ContentType=\"")
                            .append(part.contentType).append("\"/>");
            }
            sb.append("</Types>");
            return sb.toString();
        }
    }

    private static String run(String text, String rPr) {
        StringBuilder sb = new StringBuilder("<w:r>");
        if (rPr != null)
            sb.append("<w:rPr>").append(rPr).append("</w:rPr>");
        sb.append("<w:t xml:space=\"preserve\">").append(text).append("</w:t></w:r>");
        return sb.toString();
    }

    private static String paragraph(String pPr, String runXml) {
        StringBuilder sb = new StringBuilder("<w:p>");
        if (pPr != null)
            sb.append("<w:pPr>").append(pPr).append("</w:pPr>");
        sb.append(runXml).append("</w:p>");
        return sb.toString();
    }

    /**
     * Deterministic 8x8 PNG (fixed pixels, no timestamps) used as corpus media part.
     */
    static byte[] deterministicPng() {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                img.setRGB(x, y, ((x / 2 + y / 2) % 2 == 0) ? 0x336699 : 0xCCCC66);
            }
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(img, "png", out))
                throw new IllegalStateException("no PNG writer available");
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("failed to encode corpus PNG", e);
        }
    }
}
