package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.DocConvertConstants;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.xlang.api.XLang;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_HTML;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_MD;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_SHTML;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_XLSX;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_XML;

public class ExcelDocumentConverter implements IDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
        String renderType = StringHelper.lastPart(toFileType, '.');
        if (FILE_TYPE_XML.equals(renderType)) {
            if (doc.getFileExt().equals(FILE_TYPE_XLSX)) {
                ExcelWorkbook wk = ExcelDocHelper.loadExcel(doc);
                return ExcelHelper.toWorkbookXmlNode(wk).xml();
            }
            return doc.getNode(options).xml();
        }

        if (FILE_TYPE_HTML.equals(renderType)) {
            return ExcelDocHelper.renderText(doc, FILE_TYPE_HTML);
        }

        if (FILE_TYPE_SHTML.equals(renderType))
            return ExcelDocHelper.renderText(doc, FILE_TYPE_SHTML);

        if (DocConvertConstants.FILE_TYPE_MD.equals(renderType)) {
            ExcelWorkbook wk = ExcelDocHelper.loadExcel(doc);
            return new ExcelWorkbookToMarkdownConverter().convertToMarkdown(wk);
        }

        ITextTemplateOutput renderer = (ITextTemplateOutput) ExcelDocHelper.getExcelRenderer(doc, renderType);
        return renderer.generateText(XLang.newEvalScope());
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out, DocumentConvertOptions options) throws IOException {
        String renderType = StringHelper.lastPart(toFileType, '.');
        if (FILE_TYPE_XML.equals(renderType) || FILE_TYPE_MD.equals(renderType)
                || FILE_TYPE_HTML.equals(renderType) || FILE_TYPE_SHTML.equals(renderType)) {
            String text = convertToText(doc, toFileType, options);
            out.write(text.getBytes(StandardCharsets.UTF_8));
            return;
        }

        ITemplateOutput renderer = ExcelDocHelper.getExcelRenderer(doc, renderType);
        renderer.generateToStream(out, XLang.newEvalScope());
    }
}