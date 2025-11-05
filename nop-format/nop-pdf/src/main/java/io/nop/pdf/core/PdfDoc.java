package io.nop.pdf.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.pdf.tabula.ObjectExtractor;
import io.nop.pdf.tabula.Page;
import io.nop.pdf.tabula.Table;
import io.nop.pdf.tabula.TabulaTableHelper;
import io.nop.pdf.tabula.extractors.BasicExtractionAlgorithm;
import io.nop.pdf.tabula.extractors.SpreadsheetExtractionAlgorithm;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class PdfDoc implements Closeable {
    private final PDDocument doc;

    public PdfDoc() {
        this.doc = new PDDocument();
    }

    public PdfDoc(PDDocument doc) {
        this.doc = Guard.notNull(doc, "doc");
    }

    public static PdfDoc loadFromFile(File file) {
        try {
            return new PdfDoc(Loader.loadPDF(file));
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public int getNumberOfPages() {
        return doc.getNumberOfPages();
    }

    public String getPageText(int pageNum) {
        PDFTextStripper stripper = new PDFTextStripper();

        // 设置开始页和结束页（页码从1开始）
        stripper.setStartPage(pageNum);
        stripper.setEndPage(pageNum);

        try {
            return stripper.getText(doc);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public String getAllText() {
        return getAllText("====Page{pageNo}===", null, -1);
    }

    public String getAllText(String beginPattern, String endPattern, int maxSizePerPage) {
        int totalPages = getNumberOfPages();
        if (totalPages <= 0)
            return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= totalPages; i++) {
            int pageNo = i;
            String text = getPageText(i);
            if (beginPattern != null) {
                String beginText = StringHelper.renderTemplate(beginPattern, name -> {
                    if (name.equals("pageNo"))
                        return pageNo;
                    return "${" + name + "}";
                });
                sb.append(beginText);
                sb.append('\n');
            }
            if (maxSizePerPage > 0) {
                sb.append(StringHelper.limitLen(text, maxSizePerPage));
            } else {
                sb.append(text);
            }
            sb.append("\n\n");

            if (endPattern != null) {
                String endText = StringHelper.renderTemplate(endPattern, name -> {
                    if (name.equals("pageNo"))
                        return pageNo;
                    return "${" + name + "}";
                });
                sb.append(endText);
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public PdfDoc selectPages(List<Integer> pages) {
        int totalPages = getNumberOfPages();

        PDDocument ret = new PDDocument();

        for (int pageNum : pages) {
            if (pageNum >= 1 && pageNum <= totalPages) {
                // 注意：PDFBox 3.x中getPage方法可能返回的是不可变页面对象
                // 需要根据实际情况处理
                PDPage page = this.doc.getPage(pageNum - 1);
                ret.addPage(page);
            }
        }
        return new PdfDoc(ret);
    }

    public PdfDoc selectPageRange(int firstPage, int lastPage) {
        int totalPages = getNumberOfPages();

        PDDocument ret = new PDDocument();

        for (int pageNum = firstPage; pageNum <= lastPage; pageNum++) {
            if (pageNum >= 1 && pageNum <= totalPages) {
                // 注意：PDFBox 3.x中getPage方法可能返回的是不可变页面对象
                // 需要根据实际情况处理
                PDPage page = this.doc.getPage(pageNum - 1);
                ret.addPage(page);
            }
        }
        return new PdfDoc(ret);
    }

    public PdfDoc selectPage(int pageNum) {
        PDDocument ret = new PDDocument();
        if (pageNum >= 1 && pageNum <= getNumberOfPages()) {
            ret.addPage(this.doc.getPage(pageNum - 1));
        }
        return new PdfDoc(ret);
    }

    public void splitIntoPages(File dir) {
        dir.mkdirs();

        for (int i = 1, n = this.getNumberOfPages(); i < n; i++) {
            File file = new File(dir, StringHelper.leftPad(i + "", 3, '0') + ".pdf");

            selectPage(i).save(file);
        }
    }

    public List<BaseTable> extractTables(int pageNum) {
        Page page = new ObjectExtractor(doc).extract(pageNum );

        List<Table> tables = new BasicExtractionAlgorithm().extract(page);
        return tables.stream().map(TabulaTableHelper::toBaseTable).collect(Collectors.toList());
    }

    public String getAllTablesHtml() {
        StringBuilder sb = new StringBuilder();
        int totalPages = getNumberOfPages();
        for (int i = 1; i <= totalPages; i++) {
            String html = getTablesHtml(i);
            sb.append("<p>").append("Page").append(i).append("</p>\n");
            sb.append(html);
            sb.append("\n");
        }
        return sb.toString();
    }

    public String getTablesHtml(int pageNum) {
        StringBuilder sb = new StringBuilder();
        for (BaseTable table : extractTables(pageNum)) {
            sb.append(table.toHtmlString());
            sb.append('\n');
        }
        return sb.toString();
    }

    public void save(File file) {
        try {
            doc.save(file);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void close() throws IOException {
        doc.close();
    }
}
