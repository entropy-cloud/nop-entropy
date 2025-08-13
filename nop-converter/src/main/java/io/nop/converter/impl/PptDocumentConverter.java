package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.ooxml.markdown.PptxToMarkdownConverter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_MD;

public class PptDocumentConverter implements IDocumentConverter {
    @Override
    public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
        String fileExt = StringHelper.fileExtFromFileType(toFileType);
        if (FILE_TYPE_MD.equals(fileExt)) {
            return new PptxToMarkdownConverter().convertFromResource(doc.getResource()).toText();
        } else {
            throw new UnsupportedOperationException("Unsupported file type: " + toFileType);
        }
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out, DocumentConvertOptions options) throws IOException {
        String text = convertToText(doc, toFileType, options);
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
