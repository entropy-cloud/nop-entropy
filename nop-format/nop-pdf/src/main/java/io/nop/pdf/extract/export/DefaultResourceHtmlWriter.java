package io.nop.pdf.extract.export;

import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;
import io.nop.pdf.extract.struct.TextlineBlock;
import io.nop.pdf.extract.struct.TocItem;
import io.nop.api.core.exceptions.NopScriptError;
import io.nop.commons.util.StringHelper;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;


/**
 * 根据生成的html可反向构造出ResourcePage对象.
 * 生成的html元素都具有id属性, 便于程序定位
 */
public class DefaultResourceHtmlWriter {

    private String fileName = null;
    private String destFile = null;

    private Writer writer = null;

    public DefaultResourceHtmlWriter(String fileName, String destFile)
            throws IOException {

        this.fileName = fileName;
        this.destFile = destFile;
    }

    public DefaultResourceHtmlWriter(String fileName) {
        this.fileName = fileName;
    }

    public void writeDoc(ResourceDocument doc) throws IOException {

        writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(destFile), "utf-8"));
        try {
            writeDoc(doc, writer);
        } finally {
            writer.close();
        }
    }

    public void writeDoc(ResourceDocument doc, Writer out)
            throws IOException {

        this.writer = out;

        this.begin();

        this.writeToc(doc, writer);

        List<ResourcePage> pages = doc.getPages();
        for (int i = 0; i < pages.size(); i++) {

            ResourcePage page = pages.get(i);
            if (page == null)
                continue;

            this.writePage(page);
        }

        this.end();
    }

    private void writeToc(ResourceDocument doc, Writer writer) throws IOException {
        if (doc.getTocTable() != null && doc.getTocTable().getItems() != null) {
            writer.write("<ol class='pdf-toc'>");
            for (TocItem item : doc.getTocTable().getItems()) {
                writer.write("<li><a href='#p");
                writer.write(String.valueOf(item.getPageNo()));
                writer.write("'>");
                writer.write(item.getTitle());
                writer.write("</a>");
                writer.write("</li>\r\n");
            }
            writer.write("</ol>");
        }
    }

    private void begin() throws IOException {

        writer.write("<!doctype html>\r\n");
        writer.write("<html>\r\n");
        writer.write("<head>\r\n");
        writer.write("<meta charset=\"UTF-8\" />\r\n");
        writer.write("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge,chrome=1\" />\r\n");
        writer.write("<title>" + encodeText(fileName) + "</title>\r\n");
        writer.write("<style>*{ box-sizing:border-box; } "
                + ".pdf-page{position:relative;background-color:#C0C0C0; padding:10px;margin:10px}"
                + ".pdf-table, .pdf-table td{border:1px solid black}"
                + ".pdf-page-header, .pdf-page-footer{ background-color:#DDD;}"
                + " </style>");
        // /year/month/day/xxx/yy.html
        writer.write("<link href=\"../../../../pdf.css\" rel=\"stylesheet\" type=\"text/css\" charset=\"UTF-8\"/>");
        writer.write("</head>\r\n");
        writer.write("<body>\r\n");
    }

    private void end() throws IOException {
        writer.write("<script src=\"../../../../pdf_html.js\" charset=\"UTF-8\"/>");
        writer.write("</body>\r\n");
        writer.write("</html>");
    }

    private void writePage(ResourcePage page) throws IOException {

        writer.write("<div class='pdf-page' id='p" + page.getPageNo() + "'"
                + " title=\"page-" + page.getPageNo() + "|" + page.getDisplayPageNo() + "\" data-display-page-no=\"" + page.getDisplayPageNo() + "\">\r\n");

        writePageHeader(writer, page);

        writer.write("<div class='pdf-page-content'>");

        List<Block> blocks = page.getSortedBlocks();

        writeBlocks(writer, blocks);

        writer.write("</div>");

        writePageFooter(writer, page);

        writer.write("</div>\r\n");
    }

    void writePageHeader(Writer writer, ResourcePage page) throws IOException {
        if (page.getPageHeader() != null) {
            writer.write("<div class='pdf-page-header'>");
            writeBlocks(writer, page.getPageHeader());
            writer.write("</div>");
        }
    }

    void writePageFooter(Writer writer, ResourcePage page) throws IOException {
        if (page.getPageFooter() != null) {
            writer.write("<div class='pdf-page-footer'>");
            writeBlocks(writer, page.getPageFooter());
            writer.write("</div>");
        }
    }

    private void writeBlocks(Writer writer, List<Block> blocks)
            throws IOException {
        for (int i = 0; i < blocks.size(); i++) {

            Block block = blocks.get(i);

            if (block instanceof TextlineBlock) {

                this.writeTextlineBlock((TextlineBlock) block);
                continue;
            }

            if (block instanceof TableBlock) {

                this.writeTableBlock((TableBlock) block);
                continue;
            }

            if (block instanceof ImageBlock) {

                this.writeImageBlock((ImageBlock) block);
                continue;
            }

            throw new NopScriptError("pdf.err_unknown_block_type").param("block", block);
        }
    }

    private void writeTextlineBlock(TextlineBlock block) throws IOException {

        writer.write("<div class='pdf-line' id='b" + block.getPageNo() + '-' + block.getPageBlockIndex() + "'>\r\n");
        writer.write(this.encodeText(block.getContent()));
        writer.write("</div>\r\n");
    }

    private void writeTableBlock(TableBlock table) throws IOException {

        writer.write("<table class='pdf-table' id='b" + table.getPageNo() + '-' + table.getPageBlockIndex() + "' ");
        writer.write("x-pt=\"" + StringHelper.join(table.getXpoints(), ",") + "\" y-pt=\"" + StringHelper.join(table.getYpoints(), ",") + "\"");
        writer.write(">\r\n");
        for (int p = 0; p < table.getRowCount(); p++) {

            writer.write("<tr>\r\n");
            for (int q = 0; q < table.getColCount(); q++) {


                TableCellBlock cell = table.getCell(p, q);
                if (cell == null)
                    continue;

                String celltext = cell.content;
                if (celltext == null)
                    celltext = "&nbsp;";

                StringBuilder sb = new StringBuilder();
                sb.append("<td id='b").append(cell.getPageNo()).append('-').append(cell.getPageBlockIndex()).append("' ");
                if (cell.getRowspan() > 1) {
                    sb.append("rowspan='").append(cell.getRowspan())
                            .append("' ");
                }
                if (cell.getColspan() > 1) {
                    sb.append("colspan='").append(cell.getColspan())
                            .append("' ");
                }
                sb.append("r='").append(cell.getRowPos()).append("' c='").append(cell.getColPos()).append("' ");
                sb.append(">");
                sb.append(encodeText(celltext));
                sb.append("</td>\r\n");
                String td = sb.toString();
                writer.write(td);
            }
            writer.write("</tr>\r\n");
        }
        writer.write("</table>\r\n");
    }

    private void writeImageBlock(ImageBlock block) throws IOException {

        if (block == null)
            return;

        if (block.getReference() == null)
            return;
        // page-1-14.jpg
        String src = "resources/b" + block.getPageNo() + "-"
                + block.getPageBlockIndex() + ".jpg";
        writer.write("<div class='pdf-image' id='b" + block.getPageNo() + '-' + block.getPageBlockIndex() + "'>\r\n");
        writer.write("<img src=\"" + src + "\"/>");
        writer.write("</div>\r\n");
    }

    private String encodeText(String text) {
        text = StringHelper.escapeXmlValue(text);
        text = StringHelper.replace(text, "\r\n", "<br/>");
        text = StringHelper.replace(text, "\n", "<br/>");
        return text;
    }
}
