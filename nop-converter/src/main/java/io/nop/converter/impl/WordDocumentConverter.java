package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.ooxml.markdown.DocxToMarkdownConverter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_MD;

public class WordDocumentConverter implements IDocumentConverter {
    @Override
    public String convertToText(IDocumentObject doc, String toFileType) {
        String fileExt = StringHelper.fileExtFromFileType(toFileType);
        if (FILE_TYPE_MD.equals(fileExt)) {
            return new DocxToMarkdownConverter().convertFromResource(doc.getResource()).toText();
        } else {
            throw new UnsupportedOperationException("Unsupported file type: " + toFileType);
        }
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out) throws IOException {
        String text = convertToText(doc, toFileType);
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
