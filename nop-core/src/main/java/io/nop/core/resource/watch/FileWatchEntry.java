/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.watch;

import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

class FileWatchEntry {
    private final WatchKey watchKey;
    private final Path path;

    private final Path root;

    private final Predicate<Path> filter;

    private final boolean recursive;

    private final IFileWatchListener listener;

    private Map<Path, FileWatchEntry> subEntries = new ConcurrentHashMap<>();

    public FileWatchEntry(WatchKey watchKey, Path root, Path path, Predicate<Path> filter, boolean recursive,
                          IFileWatchListener listener) {
        this.root = root;
        this.path = path;
        this.filter = filter;
        this.recursive = recursive;
        this.listener = listener;
        this.watchKey = watchKey;
    }

    public IFileWatchListener getListener() {
        return listener;
    }

    public Predicate<Path> getFilter() {
        return filter;
    }

    public boolean accept(Path path) {
        if (filter == null)
            return true;
        return filter.test(path);
    }

    public Path getPath() {
        return path;
    }

    public Path getRoot() {
        return root;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public Map<Path, FileWatchEntry> getSubEntries() {
        return subEntries;
    }

    public WatchKey getWatchKey() {
        return watchKey;
    }

    public void addSubEntry(FileWatchEntry entry) {
        subEntries.put(entry.getPath(), entry);
    }

    public FileWatchEntry getSubEntry(Path subPath) {
        return subEntries.get(subPath);
    }
}