package io.nop.core.resource.zip;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLocator;
import io.nop.core.resource.VirtualFileSystem;

import java.io.File;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_FILE;

public class ZipResourceLocator implements IResourceLocator {
    public static final ZipResourceLocator INSTANCE = new ZipResourceLocator();

    static final String JAR_FILE_PREFIX = "jar:";
    static final String ZIP_FILE_SEPARATOR = "!/";
    static final String ENCODING_PARAM = "?encoding=";

    private IResourceLocator baseLocator;

    public void setBaseLocator(IResourceLocator baseLocator) {
        this.baseLocator = baseLocator;
    }

    IResourceLocator getBaseLocator() {
        if (baseLocator == null)
            return VirtualFileSystem.instance();
        return baseLocator;
    }

    @Override
    public IResource getResource(String path) {
        try {
            IResourceLocator baseLocator = getBaseLocator();

            String encoding = null;

            int pos0 = path.indexOf(ENCODING_PARAM);
            if (pos0 > 0) {
                encoding = path.substring(pos0 + ENCODING_PARAM.length());
                path = path.substring(0, pos0);
            }

            if (path.startsWith(JAR_FILE_PREFIX)) {
                path = path.substring(JAR_FILE_PREFIX.length());
            }

            int pos = path.indexOf(ZIP_FILE_SEPARATOR);
            if (pos < 0)
                return baseLocator.getResource(path);

            String entryName = path.substring(pos + ZIP_FILE_SEPARATOR.length());
            IResource resource = baseLocator.getResource(path.substring(0, pos));
            File file = resource.toFile();
            if (file == null)
                throw new NopException(ERR_RESOURCE_NOT_FILE)
                        .param(ARG_RESOURCE, resource).param(ARG_RESOURCE_PATH, resource.getPath());

            return new AutoCloseZipEntryResource(path, file, entryName, encoding);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}