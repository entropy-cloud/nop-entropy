package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.DocConvertConstants;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_MD;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_XML;

public class ExcelDocumentConverter implements IDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType) {
        String renderType = StringHelper.lastPart(toFileType, '.');
        if (FILE_TYPE_XML.equals(renderType))
            return doc.getNode().xml();

        if (DocConvertConstants.FILE_TYPE_MD.equals(renderType)) {
            ExcelWorkbook wk = ExcelDocHelper.loadExcel(doc);
            return new ExcelWorkbookToMarkdownConverter().convertToMarkdown(wk);
        }

        ITextTemplateOutput renderer = (ITextTemplateOutput) ExcelDocHelper.getExcelRenderer(doc, renderType);
        return renderer.generateText(DisabledEvalScope.INSTANCE);
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out) throws IOException {
        String renderType = StringHelper.lastPart(toFileType, '.');
        if (FILE_TYPE_XML.equals(renderType) || FILE_TYPE_MD.equals(renderType)) {
            String text = convertToText(doc, toFileType);
            out.write(text.getBytes(StandardCharsets.UTF_8));
            return;
        }

        ITemplateOutput renderer = ExcelDocHelper.getExcelRenderer(doc, renderType);
        renderer.generateToStream(out, DisabledEvalScope.INSTANCE);
    }
}