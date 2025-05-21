package io.nop.pdf.extract;

import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.core.resource.IResource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

public interface IResourceDocumentExporter {
    /**
     * 如果导出多个文件，则exportToStream/exportWriter函数不可用。exportToResource会根据给定的资源路径建立子目录
     *
     * @return
     */
    boolean isExportMutiFile();

    void exportToStream(ResourceDocument doc, OutputStream os, String encoding) throws IOException;

    void exportToWriter(ResourceDocument doc, Writer out, String encoding) throws IOException;

    void exportToResource(ResourceDocument doc, IResource resource, String encoding);

    //void exportToFile(ResourceDocument doc, File file, String encoding);
}