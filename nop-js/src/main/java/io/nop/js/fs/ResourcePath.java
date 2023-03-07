/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.js.fs;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

public class ResourcePath implements Path {
    private final ResourceFileSystem fs;
    private final String path;
    private final boolean absolute;

    public ResourcePath(ResourceFileSystem fs, String path, boolean absolute) {
        this.fs = fs;
        this.path = absolute ? StringHelper.normalizePath(path) : path;
        this.absolute = absolute;
    }

    public String toString() {
        return path;
    }

    @Override
    public FileSystem getFileSystem() {
        return fs;
    }

    @Override
    public boolean isAbsolute() {
        return absolute;
    }

    @Override
    public Path getRoot() {
        return new ResourcePath(fs, "/", true);
    }

    @Override
    public Path getFileName() {
        return new ResourcePath(fs, StringHelper.fileFullName(path), false);
    }

    @Override
    public Path getParent() {
        String parent = StringHelper.filePath(path);
        return new ResourcePath(fs, parent, absolute);
    }

    @Override
    public int getNameCount() {
        int count = StringHelper.countChar(path, '/');
        if (path.startsWith("/"))
            count--;
        return count;
    }

    @Override
    public Path getName(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean startsWith(Path other) {
        String p1 = toAbsolutePath().toString();
        String p2 = other.toAbsolutePath().toString();
        return StringHelper.pathStartsWith(p1, p2);
    }

    @Override
    public boolean endsWith(Path other) {
        String p1 = toAbsolutePath().toString();
        String p2 = other.toAbsolutePath().toString();
        return StringHelper.pathEndsWith(p1, p2);
    }

    @Override
    public Path normalize() {
        return toAbsolutePath();
    }

    @Override
    public Path resolve(Path other) {
        String p = StringHelper.absolutePath(toAbsolutePath().toString(), other.toString());
        return new ResourcePath(fs, p, true);
    }

    @Override
    public Path relativize(Path other) {
        String p1 = toAbsolutePath().toString();
        String p2 = other.toAbsolutePath().toString();
        return new ResourcePath(fs, StringHelper.relativizePath(p1, p2), false);
    }

    @Override
    public URI toUri() {
        try {
            return new URI("v", "/", toAbsolutePath().toString(), null);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public Path toAbsolutePath() {
        return fs.toAbsolutePath(this);
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return this;
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        return null;
    }

    @Override
    public int compareTo(Path other) {
        return path.compareTo(other.toString());
    }
}
