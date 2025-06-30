package io.nop.converter;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;

import java.io.IOException;
import java.io.OutputStream;

public interface IDocumentConverter {
    String convertToText(IDocumentObject doc, String toFileType);

    void convertToStream(IDocumentObject doc, String toFileType, OutputStream out) throws IOException;

    default void convertToResource(IDocumentObject doc, String toFileType, IResource resource) {
        OutputStream out = null;
        try {
            out = resource.getOutputStream();
            convertToStream(doc, toFileType, out);
            out.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(out);
        }
    }
}