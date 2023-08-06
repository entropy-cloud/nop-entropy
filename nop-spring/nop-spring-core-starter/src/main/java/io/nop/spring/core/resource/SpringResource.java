package io.nop.spring.core.resource;

import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

/**
 * 将IResource接口封装为Spring内置的Resource接口
 */
public class SpringResource implements Resource {
    private final IResource resource;

    public SpringResource(IResource resource) {
        this.resource = resource;
    }

    @Override
    public boolean exists() {
        return resource.exists();
    }

    @Override
    public URL getURL() {
        return resource.toURL();
    }

    @Override
    public URI getURI() {
        return resource.toURI();
    }

    @Override
    public File getFile() {
        return resource.toFile();
    }

    @Override
    public long contentLength() {
        return resource.length();
    }

    @Override
    public long lastModified() {
        return resource.lastModified();
    }

    @Override
    public Resource createRelative(String relativePath) throws IOException {
        if (resource instanceof IFile)
            return new SpringResource(((IFile) resource).getResource(relativePath));
        throw new IllegalArgumentException("resource not file:" + resource);
    }

    @Override
    public String getFilename() {
        return resource.getName();
    }

    @Override
    public String getDescription() {
        return resource.getPath();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return resource.getInputStream();
    }
}
