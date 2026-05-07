package io.nop.code.core.incremental;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceRegion;
import jakarta.annotation.Nonnull;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * 已删除资源的桩实现，仅记录路径信息用于 ChangeSet 构建
 */
class DeletedResourceStub implements IResource {

    private final String stdPath;

    DeletedResourceStub(String stdPath) {
        this.stdPath = stdPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeletedResourceStub)) return false;
        return stdPath.equals(((DeletedResourceStub) o).stdPath);
    }

    @Override
    public int hashCode() {
        return stdPath.hashCode();
    }

    @Override
    public String getPath() {
        return stdPath;
    }

    @Override
    public String getStdPath() {
        return stdPath;
    }

    @Override
    public String getExternalPath() {
        return stdPath;
    }

    @Override
    public String getName() {
        int idx = stdPath.lastIndexOf('/');
        return idx >= 0 ? stdPath.substring(idx + 1) : stdPath;
    }

    @Override
    public long length() {
        return -1;
    }

    @Override
    public long lastModified() {
        return 0;
    }

    @Override
    public void setLastModified(long time) {
    }

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public InputStream getInputStream() {
        throw new UnsupportedOperationException("Resource has been deleted: " + stdPath);
    }

    @Override
    public OutputStream getOutputStream(boolean append) {
        throw new UnsupportedOperationException("DeletedResourceStub does not support output");
    }

    @Override
    public File toFile() {
        return null;
    }

    @Override
    public URL toURL() {
        return null;
    }

    @Override
    public void saveToFile(@Nonnull File file) {
        throw new UnsupportedOperationException("DeletedResourceStub does not support saveToFile");
    }

    @Override
    public void saveToResource(IResource resource, IStepProgressListener listener) {
        throw new UnsupportedOperationException("DeletedResourceStub does not support saveToResource");
    }

    @Override
    public void writeToStream(OutputStream os, IStepProgressListener listener) {
        throw new UnsupportedOperationException("DeletedResourceStub does not support writeToStream");
    }

    @Override
    public IResourceRegion getResourceRegion(LongRangeBean range) {
        return null;
    }
}
