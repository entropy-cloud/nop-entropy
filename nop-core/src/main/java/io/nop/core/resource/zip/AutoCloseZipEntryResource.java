package io.nop.core.resource.zip;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.impl.AbstractResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_GET_INPUT_STREAM_FAIL;

public class AutoCloseZipEntryResource extends AbstractResource {
    static final Logger LOG = LoggerFactory.getLogger(AutoCloseZipEntryResource.class);
    private final File zipFile;
    private final Charset encoding;
    private final String entryName;

    private Entry entry;

    static class Entry {
        private final long size;
        private final long time;

        private final boolean exists;

        public Entry(long size, long time, boolean exists) {
            this.size = size;
            this.time = time;
            this.exists = exists;
        }

        public boolean isExists() {
            return exists;
        }

        public long getSize() {
            return size;
        }

        public long getTime() {
            return time;
        }
    }

    public AutoCloseZipEntryResource(String path, File zipFile, String entryName, String encoding) {
        super(path);
        this.zipFile = zipFile;
        this.entryName = entryName;
        this.encoding = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    }

    public File getZipFile() {
        return zipFile;
    }

    public String getEntryName() {
        return entryName;
    }

    public Charset getEncoding() {
        return encoding;
    }


    public URL toURL() {
        try {
            return new URL(getExternalPath());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    protected Object internalObj() {
        return getExternalPath();
    }

    @Override
    public String getExternalPath() {
        String protocol = "jar";
        String url = protocol + ":" + FileHelper.buildFileUrl(FileHelper.getCanonicalPath(new File(zipFile.getName()))) + "!/"
                + entryName;
        return url;
    }

    private synchronized Entry getEntry() {
        if (entry == null) {
            ZipFile zipFile = null;
            try {
                zipFile = new ZipFile(this.zipFile, encoding);
                ZipEntry zipEntry = zipFile.getEntry(entryName);
                if (zipEntry != null) {
                    this.entry = new Entry(zipEntry.getSize(), zipEntry.getTime(), true);
                } else {
                    this.entry = new Entry(-1, -1, false);
                }
            } catch (Exception e) {
                LOG.debug("nop.resource.get-zip-file-entry-fail", e);
                this.entry = new Entry(-1, -1, false);
            } finally {
                IoHelper.safeCloseObject(zipFile);
            }
        }
        return entry;
    }

    @Override
    public boolean exists() {
        Entry entry = getEntry();
        return entry.isExists();
    }

    @Override
    public long length() {
        return getEntry().getSize();
    }

    @Override
    public long lastModified() {
        return getEntry().getTime();
    }

    @Override
    public InputStream getInputStream() {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(this.zipFile, encoding);
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null)
                throw new NopException(ERR_RESOURCE_GET_INPUT_STREAM_FAIL).param(ARG_RESOURCE, this);

            ZipFile finalZipFile = zipFile;
            return new FilterInputStream(zipFile.getInputStream(entry)) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        IoHelper.safeCloseObject(finalZipFile);
                    }
                }
            };
        } catch (Exception e) {
            IoHelper.safeCloseObject(zipFile);
            if (e instanceof NopException)
                throw (NopException) e;
            throw new NopException(ERR_RESOURCE_GET_INPUT_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
    }
}
