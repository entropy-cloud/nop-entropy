package io.nop.pdf.extract.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.pdf.extract.IResourceDocumentExporter;
import io.nop.pdf.extract.struct.ResourceDocument;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

public abstract class AbstractResourceDocumentExporter implements IResourceDocumentExporter {

    @Override
    public boolean isExportMutiFile() {
        return false;
    }

    public void exportToFile(ResourceDocument doc, File file, String encoding) {
        exportToResource(doc, new FileResource(file), encoding);
    }

    @Override
    public void exportToResource(ResourceDocument doc, IResource resource, String encoding) {
        OutputStream os = null;
        try {
            os = resource.getOutputStream();
            os = new BufferedOutputStream(os);
            exportToStream(doc, os, encoding);
            os.flush();
        } catch (Exception e) {
            throw NopException.wrap(e);
        } finally {
            IoHelper.safeClose(os);
        }
    }

    @Override
    public void exportToWriter(ResourceDocument doc, Writer out, String encoding) throws IOException {
        throw new UnsupportedEncodingException();
    }

    @Override
    public void exportToStream(ResourceDocument doc, OutputStream os, String encoding) throws IOException {
        if (encoding == null)
            encoding = StringHelper.ENCODING_UTF8;
        Writer writer = new OutputStreamWriter(os, encoding);
        exportToWriter(doc, writer, encoding);
        writer.flush();
    }
}