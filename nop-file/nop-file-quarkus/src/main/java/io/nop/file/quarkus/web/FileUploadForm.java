package io.nop.file.quarkus.web;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.InputStreamResource;
import io.nop.file.core.IFileRecord;
import org.jboss.resteasy.annotations.providers.multipart.PartType;

import javax.ws.rs.FormParam;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

public class FileUploadForm implements IFileRecord {

    @FormParam("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private InputStream file;

    @FormParam("file")
    @PartType(MediaType.TEXT_PLAIN)
    private String fileName;

    public InputStream getFile() {
        return file;
    }

    public void setFile(InputStream file) {
        this.file = file;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public String getFileId() {
        return null;
    }

    @Override
    public String getFileExt() {
        return StringHelper.fileExt(fileName);
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public long getLength() {
        return -1;
    }

    @Override
    public long getLastModified() {
        return 0;
    }

    @Override
    public IResource getResource() {
        return new InputStreamResource("/upload", file, getLastModified());
    }
}