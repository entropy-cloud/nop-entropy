package io.nop.converter.impl;

import io.nop.commons.util.IoHelper;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.core.resource.IResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SameDocumentConverter implements IDocumentConverter {
    public static SameDocumentConverter INSTANCE = new SameDocumentConverter();

    @Override
    public String convertToText(IDocumentObject doc, String toFileType) {
        return doc.getText();
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out)
            throws IOException {
        IResource resource = doc.getResource();
        InputStream is = null;
        try {
            is = resource.getInputStream();
            IoHelper.copy(is, out);
            out.flush();
        } finally {
            IoHelper.safeCloseObject(is);
        }
    }
}
